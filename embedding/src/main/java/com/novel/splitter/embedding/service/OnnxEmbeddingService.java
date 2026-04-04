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

@Slf4j
@Service
@Primary // Make this the default implementation
@RequiredArgsConstructor
public class OnnxEmbeddingService implements EmbeddingService {

    private final OnnxModelHolder modelHolder;
    private final Tokenizer tokenizer;

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        int batchSize = texts.size();
        long[][] inputIdsBatch = new long[batchSize][];
        long[][] attentionMaskBatch = new long[batchSize][];
        long[][] tokenTypeIdsBatch = new long[batchSize][];

        // 1. Tokenize all texts
        for (int i = 0; i < batchSize; i++) {
            TokenizedInput input = tokenizer.tokenize(texts.get(i));
            inputIdsBatch[i] = input.getInputIds();
            attentionMaskBatch[i] = input.getAttentionMask();
            tokenTypeIdsBatch[i] = input.getTokenTypeIds();
        }

        // 2. Prepare Batched Tensors
        // The length of each tokenized input is fixed to MAX_LENGTH (512) in Tokenizer
        int seqLength = inputIdsBatch[0].length;
        long[] shape = new long[]{batchSize, seqLength};

        OnnxTensor inputIdsTensor = null;
        OnnxTensor attentionMaskTensor = null;
        OnnxTensor tokenTypeIdsTensor = null;
        OrtSession.Result result = null;

        try {
            // Flatten 2D arrays to 1D for Tensor creation
            long[] flatInputIds = new long[batchSize * seqLength];
            long[] flatAttentionMask = new long[batchSize * seqLength];
            long[] flatTokenTypeIds = new long[batchSize * seqLength];

            for (int i = 0; i < batchSize; i++) {
                System.arraycopy(inputIdsBatch[i], 0, flatInputIds, i * seqLength, seqLength);
                System.arraycopy(attentionMaskBatch[i], 0, flatAttentionMask, i * seqLength, seqLength);
                System.arraycopy(tokenTypeIdsBatch[i], 0, flatTokenTypeIds, i * seqLength, seqLength);
            }

            inputIdsTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(flatInputIds), shape);
            attentionMaskTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(flatAttentionMask), shape);
            tokenTypeIdsTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(flatTokenTypeIds), shape);

            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);

            // 3. Run Inference (True Batching)
            result = modelHolder.getSession().run(inputs);

            // 4. Extract Output
            // Output shape is [batchSize, seqLength, hiddenSize]
            float[][][] lastHiddenState = (float[][][]) result.get(0).getValue();

            // 5. Pooling and Normalize
            List<float[]> embeddings = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                // CLS Pooling: get the first token's embedding for each sequence in the batch
                float[] clsEmbedding = lastHiddenState[i][0];
                embeddings.add(normalize(clsEmbedding));
            }

            return embeddings;

        } catch (Exception e) {
            log.error("Batch embedding failed for {} texts", batchSize, e);
            throw new RuntimeException("Batch embedding failed", e);
        } finally {
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

    private float[] normalize(float[] v) {
        double norm = 0.0;
        for (float val : v) {
            norm += val * val;
        }
        norm = Math.sqrt(norm);
        
        float[] normalized = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            normalized[i] = (float) (v[i] / norm);
        }
        return normalized;
    }
}
