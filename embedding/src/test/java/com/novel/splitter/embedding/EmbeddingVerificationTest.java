package com.novel.splitter.embedding;

import com.novel.splitter.embedding.api.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

@Slf4j
@SpringBootTest(classes = EmbeddingTestConfig.class)
public class EmbeddingVerificationTest {

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    public void testSmoke() {
        // 8️⃣ 系统级 smoke test
        log.info("Running System Smoke Test");
        float[] vector = embeddingService.embed("萧炎");
        System.out.println("Smoke Test Result for '萧炎': " + Arrays.toString(vector));
        assertNotNull(vector);
        assertTrue(vector.length > 0);
    }

    @Test
    public void testDimension() {
        log.info("Running Case 1: Dimension Verification");
        String text = "你好";
        float[] vector = embeddingService.embed(text);
        
        assertNotNull(vector, "Vector should not be null");
        // BGE-Small-ZH should be 512. Base/Large are 768 or 1024.
        // Let's assert it is one of standard sizes.
        log.info("Vector dimension: {}", vector.length);
        assertTrue(vector.length == 512 || vector.length == 768, 
                "Vector dimension should be 512 or 768, actual: " + vector.length);
        
        // Check for NaN or Infinity
        for (float v : vector) {
            assertFalse(Float.isNaN(v), "Vector contains NaN");
            assertFalse(Float.isInfinite(v), "Vector contains Infinity");
        }
    }

    @Test
    public void testStability() {
        log.info("Running Case 2: Stability Verification");
        String text = "你好";
        float[] v1 = embeddingService.embed(text);
        float[] v2 = embeddingService.embed(text);
        
        double similarity = cosineSimilarity(v1, v2);
        log.info("Self-similarity: {}", similarity);
        
        assertTrue(similarity > 0.999, "Same input should produce identical embeddings");
    }

    @Test
    public void testSemanticDistinction() {
        log.info("Running Case 3: Semantic Distinction Verification");
        String text1 = "你好";
        String text2 = "再见";
        
        float[] v1 = embeddingService.embed(text1);
        float[] v2 = embeddingService.embed(text2);
        
        double similarity = cosineSimilarity(v1, v2);
        log.info("Similarity between '{}' and '{}': {}", text1, text2, similarity);
        
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
