package com.novel.splitter.embedding.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtSession;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.onnx.OnnxModelHolder;
import com.novel.splitter.embedding.tokenizer.TokenizedInput;
import com.novel.splitter.embedding.tokenizer.Tokenizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 ONNX Runtime 的文本向量化服务实现。
 * <p>
 * 该服务负责将批量文本通过分词器（Tokenizer）处理后，输入到加载的 ONNX 模型中进行推理，
 * 并对输出的向量进行池化（CLS Pooling）和归一化（L2 Normalization）处理。
 * 在 NLP/RAG 系统中，主要用于将小说片段转化为高维稠密向量。
 * </p>
 */
@Slf4j
@Service
@Primary // Make this the default implementation
@RequiredArgsConstructor
public class OnnxEmbeddingService implements EmbeddingService {

    private final OnnxModelHolder modelHolder;
    private final Tokenizer tokenizer;

    /**
     * 对批量文本进行向量化处理。
     *
     * @param texts 需要向量化的文本列表
     * @return 对应的特征向量列表。如果输入为空，则返回空列表。
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        int batchSize = texts.size();
        long[][] inputIdsBatch = new long[batchSize][];
        long[][] attentionMaskBatch = new long[batchSize][];
        long[][] tokenTypeIdsBatch = new long[batchSize][];

        // 1. Tokenize all texts (对所有文本进行分词处理)
        for (int i = 0; i < batchSize; i++) {
            TokenizedInput input = tokenizer.tokenize(texts.get(i));
            inputIdsBatch[i] = input.getInputIds();
            attentionMaskBatch[i] = input.getAttentionMask();
            tokenTypeIdsBatch[i] = input.getTokenTypeIds();
        }

        // 2. Prepare Batched Tensors (准备批处理张量)
        // The length of each tokenized input is fixed to MAX_LENGTH (512) in Tokenizer
        // (在 Tokenizer 中，每个分词输入的长度被固定为最大长度 MAX_LENGTH，通常为 512)
        int seqLength = inputIdsBatch[0].length;
        long[] shape = new long[]{batchSize, seqLength};

        OnnxTensor inputIdsTensor = null;
        OnnxTensor attentionMaskTensor = null;
        OnnxTensor tokenTypeIdsTensor = null;
        OrtSession.Result result = null;

        try {
            // Flatten 2D arrays to 1D for Tensor creation (将二维数组展平为一维，用于创建张量)
            long[] flatInputIds = new long[batchSize * seqLength];
            long[] flatAttentionMask = new long[batchSize * seqLength];
            long[] flatTokenTypeIds = new long[batchSize * seqLength];

            for (int i = 0; i < batchSize; i++) {
                System.arraycopy(inputIdsBatch[i], 0, flatInputIds, i * seqLength, seqLength);
                System.arraycopy(attentionMaskBatch[i], 0, flatAttentionMask, i * seqLength, seqLength);
                System.arraycopy(tokenTypeIdsBatch[i], 0, flatTokenTypeIds, i * seqLength, seqLength);
            }

            // 创建 ONNX 张量对象
            inputIdsTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(flatInputIds), shape);
            attentionMaskTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(flatAttentionMask), shape);
            tokenTypeIdsTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(flatTokenTypeIds), shape);

            // 组装模型输入参数
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);

            // 3. Run Inference (True Batching) (执行推理，真正的批处理)
            result = modelHolder.getSession().run(inputs);

            // 4. Extract Output (提取输出结果)
            // Output shape is [batchSize, seqLength, hiddenSize] (输出形状为 [批次大小, 序列长度, 隐藏层维度])
            float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();

            // 5. Pooling and Normalize (池化与归一化)
            List<float[]> embeddings = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                // CLS Pooling: get the first token's embedding for each sequence in the batch
                // (CLS 池化：获取批次中每个序列的第一个 token（即 [CLS] token）的嵌入向量作为句子向量)
                float[] clsEmbedding = lastHiddenState[i][0];
                embeddings.add(normalize(clsEmbedding)); // 对向量进行 L2 归一化后加入结果集
            }

            return embeddings;

        } catch (Exception e) {
            log.error("Batch embedding failed for {} texts", batchSize, e);
            throw new RuntimeException("Batch embedding failed", e);
        } finally {
            // 确保释放 ONNX 运行时资源，防止内存泄漏
            try {
                if (result != null) result.close();
                if (inputIdsTensor != null) inputIdsTensor.close();
                if (attentionMaskTensor != null) attentionMaskTensor.close();
                if (tokenTypeIdsTensor != null) tokenTypeIdsTensor.close();
            } catch (Exception e) {
                log.warn("Error closing ONNX resources", e);
            }
        }
    }

    /**
     * 对给定的浮点数组进行 L2 归一化。
     * 
     * @param v 原始向量
     * @return 归一化后的向量
     */
    private float[] normalize(float[] v) {
        double norm = 0.0;
        // 计算向量的 L2 范数（各元素平方和的平方根）
        for (float val : v) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);
        
        float[] normalized = new float[v.length];
        // 将向量中的每个元素除以范数
        for (int i = 0; i < v.length; i++) {
            normalized[i] = (float) (v[i] / norm);
        }
        return normalized;
    }
}
