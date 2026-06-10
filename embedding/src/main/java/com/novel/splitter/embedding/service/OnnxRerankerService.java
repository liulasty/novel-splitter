package com.novel.splitter.embedding.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基于 ONNX Runtime + bge-reranker-base 的重排服务。
 * <p>
 * 接收用户问题与候选文档列表，通过交叉编码器对每个 [Query, Document] 对进行相关性打分，
 * 输出 0~1 的分数用于精准排序。通常放在向量粗召回之后，对 TopK 候选进行精排。
 * </p>
 */
@Slf4j
@Service
public class OnnxRerankerService {

    private static final String MODEL_PATH = "/reranker/model.onnx";
    private static final String TOKENIZER_PATH = "/reranker/tokenizer.json";

    private static final int MAX_LENGTH = 512;

    private OrtEnvironment env;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private volatile boolean available = false;

    /**
     * 外部模型路径（可选）。为空时从 classpath 加载，非空时从文件系统加载。
     * 通过 application.yml 或环境变量注入：
     * <pre>
     * assembler:
     *   reranker:
     *     model-path: /opt/rag/models/reranker/
     * </pre>
     */
    @Value("${assembler.reranker.model-path:}")
    private String modelPath = "";

    /**
     * 返回重排服务是否可用（模型加载成功）
     */
    public boolean isAvailable() {
        return available;
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing ONNX Reranker model... modelPath='{}'", modelPath);

            env = OrtEnvironment.getEnvironment();

            // 先加载 tokenizer（失败直接返回，不创建 session 避免泄漏）
            if (!modelPath.isBlank()) {
                Path tokenizerFile = Path.of(modelPath, "tokenizer.json");
                if (!Files.exists(tokenizerFile)) {
                    log.warn("Reranker tokenizer not found at {}, service will be unavailable", tokenizerFile);
                    return;
                }
                try (InputStream tokenizerIs = Files.newInputStream(tokenizerFile)) {
                    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerIs, Map.of(
                            "padding", "true",
                            "maxLength", String.valueOf(MAX_LENGTH),
                            "truncation", "true"
                    ));
                }
            } else {
                try (InputStream tokenizerIs = getClass().getResourceAsStream(TOKENIZER_PATH)) {
                    if (tokenizerIs == null) {
                        log.warn("Reranker tokenizer not found at {}, service will be unavailable", TOKENIZER_PATH);
                        return;
                    }
                    tokenizer = HuggingFaceTokenizer.newInstance(tokenizerIs, Map.of(
                            "padding", "true",
                            "maxLength", String.valueOf(MAX_LENGTH),
                            "truncation", "true"
                    ));
                }
            }

            // 再加载模型
            byte[] modelBytes;
            if (!modelPath.isBlank()) {
                Path modelFile = Path.of(modelPath, "model.onnx");
                if (!Files.exists(modelFile)) {
                    log.warn("Reranker model not found at {}, service will be unavailable", modelFile);
                    return;
                }
                modelBytes = Files.readAllBytes(modelFile);
            } else {
                try (InputStream modelIs = getClass().getResourceAsStream(MODEL_PATH)) {
                    if (modelIs == null) {
                        log.warn("Reranker model not found at {}, service will be unavailable", MODEL_PATH);
                        return;
                    }
                    modelBytes = modelIs.readAllBytes();
                }
            }

            // 创建 ONNX session（try-with-resources 确保 SessionOptions 及时释放）
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setInterOpNumThreads(1);
                options.setIntraOpNumThreads(2);
                session = env.createSession(modelBytes, options);
            }

            log.info("Reranker model loaded. Inputs: {}, Outputs: {}",
                    session.getInputNames(), session.getOutputNames());

            this.available = true;
            log.info("ONNX Reranker service initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize ONNX Reranker, service will be unavailable", e);
        }
    }

    /**
     * 对候选文档列表进行批量重排打分（Batch 推理）。
     * <p>
     * 所有 [query, doc] 对一次性编码 + 一次前向传播，显著提升吞吐量。
     * 当 batch 推理异常时降级为逐条串行。
     *
     * @param query   用户问题
     * @param docList 候选文档列表
     * @return 每个文档对应的相关性分数（0~1），顺序与 docList 一致
     */
    public List<Float> rerank(String query, List<String> docList) {
        if (query == null || query.isBlank() || docList == null || docList.isEmpty()) {
            return List.of();
        }

        int batchSize = docList.size();

        // 1. 批量编码，同时确定最大序列长度
        List<Encoding> encodings = new ArrayList<>(batchSize);
        int maxSeqLen = 0;
        for (String doc : docList) {
            if (doc == null || doc.isBlank()) {
                encodings.add(null);
                continue;
            }
            Encoding enc = tokenizer.encode(query, doc);
            encodings.add(enc);
            maxSeqLen = Math.max(maxSeqLen, enc.getIds().length);
        }
        maxSeqLen = Math.min(maxSeqLen, MAX_LENGTH);

        // 所有文档均为空时直接返回零分，避免 [N, 0] 张量
        if (maxSeqLen == 0) {
            List<Float> zeros = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) zeros.add(0.0f);
            return zeros;
        }

        // 2. 拼接批量输入张量 [batchSize, maxSeqLen]
        long[] shape = {batchSize, maxSeqLen};
        long[] allInputIds = new long[batchSize * maxSeqLen];
        long[] allAttentionMask = new long[batchSize * maxSeqLen];

        for (int i = 0; i < batchSize; i++) {
            Encoding enc = encodings.get(i);
            if (enc == null) continue;
            long[] ids = enc.getIds();
            long[] mask = enc.getAttentionMask();
            int len = Math.min(ids.length, maxSeqLen);
            System.arraycopy(ids, 0, allInputIds, i * maxSeqLen, len);
            System.arraycopy(mask, 0, allAttentionMask, i * maxSeqLen, len);
        }

        // 3. 批量推理
        try (OnnxTensor idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(allInputIds), shape);
             OnnxTensor maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(allAttentionMask), shape);
             OrtSession.Result result = session.run(Map.of(
                     "input_ids", idsTensor,
                     "attention_mask", maskTensor
             ))) {

            // logits shape: [batchSize, 1]
            float[][] logits = (float[][]) result.get(0).getValue();
            List<Float> scores = new ArrayList<>(batchSize);
            for (float[] logit : logits) {
                scores.add(sigmoid(logit[0]));
            }
            return scores;

        } catch (Exception e) {
            log.warn("Reranker batch inference failed, falling back to serial per-item", e);
            // 降级：逐条串行
            int failedCount = 0;
            int validCount = 0;
            List<Float> scores = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                if (docList.get(i) == null || docList.get(i).isBlank()) {
                    scores.add(0.0f);
                } else {
                    validCount++;
                    float s = calcScore(query, docList.get(i));
                    if (s == 0.0f) failedCount++;
                    scores.add(s);
                }
            }
            // 串行降级也全部失败→抛出异常，让上层回退到启发式评分
            if (validCount > 0 && failedCount == validCount) {
                throw new RuntimeException("Reranker serial fallback failed for all valid docs", e);
            }
            return scores;
        }
    }

    /**
     * 单条 query + doc 打分
     */
    private float calcScore(String query, String doc) {
        Encoding encoding = tokenizer.encode(query, doc);
        long[] inputIds = encoding.getIds();
        long[] attentionMask = encoding.getAttentionMask();

        // Ensure sequence length matches what ONNX model expects
        int seqLen = Math.min(inputIds.length, MAX_LENGTH);
        long[] shape = {1, seqLen};

        // Truncate/pad to MAX_LENGTH
        long[] trimmedIds = new long[seqLen];
        long[] trimmedMask = new long[seqLen];
        System.arraycopy(inputIds, 0, trimmedIds, 0, seqLen);
        System.arraycopy(attentionMask, 0, trimmedMask, 0, seqLen);

        try (OnnxTensor idsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(trimmedIds), shape);
             OnnxTensor maskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(trimmedMask), shape);
             OrtSession.Result result = session.run(Map.of(
                     "input_ids", idsTensor,
                     "attention_mask", maskTensor
             ))) {

            // logits shape: [1, 1]
            float[][] logits = (float[][]) result.get(0).getValue();
            float logit = logits[0][0];

            // Sigmoid 激活，转为 0~1 分数
            return sigmoid(logit);

        } catch (OrtException e) {
            log.warn("Reranker inference failed for doc: {}", truncate(doc, 50), e);
            return 0.0f;
        }
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    @PreDestroy
    public void destroy() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            log.warn("Error closing reranker session", e);
        }
    }
}
