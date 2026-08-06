package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@SpringBootTest(classes = {ChromaVectorStore.class, ChromaIntegrationTest.TestConfig.class})
@TestPropertySource(properties = {
    "embedding.store.type=chroma",
    "chroma.url=http://localhost:8081",
    "chroma.collection=test-collection",
    "chroma.init.eager=false"
})
public class ChromaIntegrationTest {

    @Autowired(required = false)
    private ChromaVectorStore chromaVectorStore;

    @TestConfiguration
    static class TestConfig {
        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    @Test
    public void testChromaIntegration() {
        if (chromaVectorStore == null) {
            System.out.println("ChromaVectorStore is not available, skipping test.");
            return;
        }

        // 创建一个虚拟场景
        String id = UUID.randomUUID().toString();
        Scene scene = Scene.builder()
                .persistenceId(1L)
                .id(id)
                .chapterTitle("Test Chapter")
                .chapterIndex(1)
                .startParagraphIndex(0)
                .endParagraphIndex(1)
                .text("This is a test sentence for ChromaDB.")
                .wordCount(10)
                .canSplit(false)
                .metadata(SceneMetadata.builder()
                        .novel("novel-itest-1")
                        .version("v-itest")
                        .chunkSize(350)
                        .chunkOverlap(65)
                        .sequenceNum(0)
                        .build())
                .build();

        // 创建一个虚拟向量（为简单起见用 10 维，实际为 512/768 维）
        // 若 collection 已存在且维度不同，Chroma 可能对维度有特定要求；
        // 但对于新建 collection，首次插入的向量即决定维度。这里使用较小的维度。
        float[] embedding = new float[10];
        for (int i = 0; i < 10; i++) {
            embedding[i] = (float) Math.random();
        }

        // 保存
        try {
            chromaVectorStore.save(scene, embedding);
            System.out.println("Saved scene to ChromaDB");
        } catch (Exception e) {
            System.err.println("Failed to save to ChromaDB: " + e.getMessage());
            // 在没有可访问的本地 ChromaDB 服务时跳过该集成测试。
            Assumptions.abort("Skipping Chroma integration test: " + e.getMessage());
        }

        // 检索
        List<VectorRecord> results = chromaVectorStore.search(embedding, 1);
        
        Assertions.assertNotNull(results);
        Assertions.assertFalse(results.isEmpty());
        Assertions.assertEquals(id, results.get(0).getChunkId());
        
        System.out.println("Search successful, found ID: " + results.get(0).getChunkId());
    }
}
