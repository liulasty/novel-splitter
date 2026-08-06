package com.novel.splitter.embedding;

import com.novel.splitter.embedding.api.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import java.util.Collections;

@Slf4j
@SpringBootTest(classes = EmbeddingTestConfig.class)
public class EmbeddingVerificationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    public void testSmoke() {
        // 8️⃣ 系统级 smoke test
        log.info("运行系统级 Smoke 测试");
        float[] vector = embeddingService.embedBatch(Collections.singletonList("萧炎")).get(0);
        System.out.println("Smoke Test Result for '萧炎': " + Arrays.toString(vector));
        assertNotNull(vector);
        assertTrue(vector.length > 0);
    }

    @Test
    public void testDimension() {
        log.info("运行用例 1：维度验证");
        String text = "你好";
        float[] vector = embeddingService.embedBatch(Collections.singletonList(text)).get(0);
        
        assertNotNull(vector, "Vector should not be null");
        // BGE-Small-ZH 应为 512 维，Base/Large 为 768 或 1024 维。
        // 这里断言其为标准维度之一。
        log.info("向量维度：{}", vector.length);
        assertTrue(vector.length == 512 || vector.length == 768, 
                "Vector dimension should be 512 or 768, actual: " + vector.length);
        
        // 检查是否包含 NaN 或 Infinity
        for (float v : vector) {
            assertFalse(Float.isNaN(v), "Vector contains NaN");
            assertFalse(Float.isInfinite(v), "Vector contains Infinity");
        }
    }

    @Test
    public void testStability() {
        log.info("运行用例 2：稳定性验证");
        String text = "你好";
        float[] v1 = embeddingService.embedBatch(Collections.singletonList(text)).get(0);
        float[] v2 = embeddingService.embedBatch(Collections.singletonList(text)).get(0);
        
        double similarity = cosineSimilarity(v1, v2);
        log.info("自相似度：{}", similarity);
        
        assertTrue(similarity > 0.999, "Same input should produce identical embeddings");
    }

    @Test
    public void testSemanticDistinction() {
        log.info("运行用例 3：语义区分度验证");
        String text1 = "你好";
        String text2 = "再见";
        
        float[] v1 = embeddingService.embedBatch(Collections.singletonList(text1)).get(0);
        float[] v2 = embeddingService.embedBatch(Collections.singletonList(text2)).get(0);
        
        double similarity = cosineSimilarity(v1, v2);
        log.info("'{}' 与 '{}' 的相似度：{}", text1, text2, similarity);
        
        assertTrue(similarity < 0.95, "Different meanings should have lower similarity");
    }
    
    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) return 0;
        
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        
        if (normA == 0 || normB == 0) return 0;
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
