package com.novel.splitter.embedding.mock;

import com.novel.splitter.embedding.api.EmbeddingService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Mock 嵌入服务
 * <p>
 * 不依赖真实模型，仅返回随机向量或全零向量，用于跑通流程。
 * </p>
 */
@Slf4j
public class MockEmbeddingService implements EmbeddingService {

    private final int dimension;

    public MockEmbeddingService(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        log.debug("Mock embedding for text: {}...", text.substring(0, Math.min(20, text.length())));
        return generateRandomVector(dimension);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        log.info("Mock embedding batch for {} items", texts.size());
        List<float[]> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(embed(text));
        }
        return result;
    }

    private float[] generateRandomVector(int dim) {
        float[] vector = new float[dim];
        Random random = new Random();
        for (int i = 0; i < dim; i++) {
            vector[i] = random.nextFloat();
        }
        return vector;
    }
}
