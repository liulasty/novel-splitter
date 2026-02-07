package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryVectorStoreTest {

    private InMemoryVectorStore vectorStore;
    private static final String STORE_FILE = "vector_store.json";

    @BeforeEach
    void setUp() {
        vectorStore = new InMemoryVectorStore();
        // Clear any existing store file
        new File(STORE_FILE).delete();
    }

    @AfterEach
    void tearDown() {
        new File(STORE_FILE).delete();
    }

    @Test
    void testSaveAndSearch() {
        String id = UUID.randomUUID().toString();
        Scene scene = Scene.builder().id(id).build();
        float[] vector = {1.0f, 0.0f, 0.0f}; // X-axis

        vectorStore.save(scene, vector);

        // Search with same vector
        List<VectorRecord> results = vectorStore.search(vector, 1);
        assertEquals(1, results.size());
        assertEquals(id, results.get(0).getChunkId());
        assertEquals(1.0, results.get(0).getScore(), 0.0001);

        // Search with orthogonal vector (Y-axis)
        float[] orthogonalVector = {0.0f, 1.0f, 0.0f};
        results = vectorStore.search(orthogonalVector, 1);
        // Cosine similarity of orthogonal vectors is 0
        assertEquals(1, results.size());
        assertEquals(0.0, results.get(0).getScore(), 0.0001);
    }

    @Test
    void testTopK() {
        // v1: (1, 0)
        Scene s1 = Scene.builder().id("1").build();
        vectorStore.save(s1, new float[]{1.0f, 0.0f});

        // v2: (0.707, 0.707) ~45 degrees
        Scene s2 = Scene.builder().id("2").build();
        vectorStore.save(s2, new float[]{0.7071f, 0.7071f});

        // v3: (0, 1) 90 degrees
        Scene s3 = Scene.builder().id("3").build();
        vectorStore.save(s3, new float[]{0.0f, 1.0f});

        // Query: (1, 0) -> Should match s1 (1.0), s2 (~0.707), s3 (0.0)
        List<VectorRecord> results = vectorStore.search(new float[]{1.0f, 0.0f}, 2);

        assertEquals(2, results.size());
        assertEquals("1", results.get(0).getChunkId());
        assertEquals("2", results.get(1).getChunkId());
    }

    @Test
    void testPersistence() {
        Scene s1 = Scene.builder().id("persist-1").build();
        vectorStore.save(s1, new float[]{0.5f, 0.5f});

        // Persist to disk
        vectorStore.persist();

        // New instance
        InMemoryVectorStore newStore = new InMemoryVectorStore();
        newStore.load();

        List<VectorRecord> results = newStore.search(new float[]{0.5f, 0.5f}, 1);
        assertEquals(1, results.size());
        assertEquals("persist-1", results.get(0).getChunkId());
    }
}
