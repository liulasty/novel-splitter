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
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[0]; 
        }

        OnnxTensor inputIdsTensor = null;
        OnnxTensor attentionMaskTensor = null;
        OnnxTensor tokenTypeIdsTensor = null;
        OrtSession.Result result = null;

        try {
            // 1. Tokenize
            TokenizedInput input = tokenizer.tokenize(text);
            
            // 2. Prepare Tensors
            long[] inputIds = input.getInputIds();
            long[] attentionMask = input.getAttentionMask();
            long[] tokenTypeIds = input.getTokenTypeIds();
            
            long[] shape = new long[]{1, inputIds.length};
            
            // Create tensors
            inputIdsTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(inputIds), shape);
            attentionMaskTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(attentionMask), shape);
            tokenTypeIdsTensor = OnnxTensor.createTensor(modelHolder.getEnv(), LongBuffer.wrap(tokenTypeIds), shape);
            
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);
            
            // 3. Run Inference
            result = modelHolder.getSession().run(inputs);
            
            // 4. Extract Output
            // Assuming the first output is last_hidden_state or pooler_output.
            // BGE-Small-ZH output 0 is usually last_hidden_state [1, 512, 512]
            float[][][] lastHiddenState = (float[][][]) result.get(0).getValue(); 
            
            // 5. Pooling (CLS Strategy for BGE)
            float[] clsEmbedding = lastHiddenState[0][0];
            
            // 6. Normalize (L2)
            return normalize(clsEmbedding);
            
        } catch (Exception e) {
            log.error("Embedding failed for text: {}", text, e);
            throw new RuntimeException("Embedding failed", e);
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

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
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
