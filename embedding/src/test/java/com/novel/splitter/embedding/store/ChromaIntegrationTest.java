package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.embedding.config.EmbeddingConfig;
import com.novel.splitter.embedding.store.ChromaVectorStore;
import com.novel.splitter.embedding.api.VectorRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

@SpringBootTest(classes = {ChromaVectorStore.class, EmbeddingConfig.class})
@TestPropertySource(properties = {
    "embedding.store.type=chroma",
    "chroma.url=http://localhost:8081",
    "chroma.collection=test-collection"
})
public class ChromaIntegrationTest {

    @Autowired(required = false)
    private ChromaVectorStore chromaVectorStore;

    @Test
    public void testChromaIntegration() {
        if (chromaVectorStore == null) {
            System.out.println("ChromaVectorStore is not available, skipping test.");
            return;
        }

        // Create a dummy scene
        String id = UUID.randomUUID().toString();
        Scene scene = Scene.builder()
                .id(id)
                .chapterTitle("Test Chapter")
                .chapterIndex(1)
                .startParagraphIndex(0)
                .endParagraphIndex(1)
                .text("This is a test sentence for ChromaDB.")
                .wordCount(10)
                .canSplit(false)
                .metadata(SceneMetadata.builder().build())
                .build();

        // Create a dummy embedding (dimension 10 for simplicity, though real is 512/768)
        // Chroma might expect specific dimensions if collection already exists with different dims.
        // But for new collection, first insert defines dim.
        // Let's use a small dimension.
        float[] embedding = new float[10];
        for (int i = 0; i < 10; i++) {
            embedding[i] = (float) Math.random();
        }

        // Save
        try {
            chromaVectorStore.save(scene, embedding);
            System.out.println("Saved scene to ChromaDB");
        } catch (Exception e) {
            System.err.println("Failed to save to ChromaDB: " + e.getMessage());
            // If connection fails, fail the test
            Assertions.fail("Failed to connect to ChromaDB: " + e.getMessage());
        }

        // Search
        List<VectorRecord> results = chromaVectorStore.search(embedding, 1);
        
        Assertions.assertNotNull(results);
        Assertions.assertFalse(results.isEmpty());
        Assertions.assertEquals(id, results.get(0).getChunkId());
        
        System.out.println("Search successful, found ID: " + results.get(0).getChunkId());
    }
}
