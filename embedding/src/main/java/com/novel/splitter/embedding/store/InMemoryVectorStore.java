package com.novel.splitter.embedding.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量存储实现
 * <p>
 * 提供简单的内存向量存储和检索功能。
 * 支持持久化到本地 JSON 文件。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "embedding.store.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private static final String STORE_FILE = "vector_store.json";
    private final Map<String, float[]> vectorMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void load() {
        File file = new File(STORE_FILE);
        if (file.exists()) {
            try {
                Map<String, float[]> loaded = objectMapper.readValue(file, new TypeReference<Map<String, float[]>>() {});
                vectorMap.putAll(loaded);
                log.info("Loaded {} vectors from {}", vectorMap.size(), STORE_FILE);
            } catch (IOException e) {
                log.error("Failed to load vector store from file", e);
            }
        } else {
            log.info("No existing vector store found at {}, starting fresh.", STORE_FILE);
        }
    }

    @PreDestroy
    public void persist() {
        try {
            objectMapper.writeValue(new File(STORE_FILE), vectorMap);
            log.info("Persisted {} vectors to {}", vectorMap.size(), STORE_FILE);
        } catch (IOException e) {
            log.error("Failed to persist vector store", e);
        }
    }

    /**
     * 清空存储 (用于测试)
     */
    public void clear() {
        vectorMap.clear();
        log.info("Vector store cleared.");
    }

    @Override
    public void save(Scene scene, float[] embedding) {
        if (scene == null || scene.getId() == null) {
            log.warn("Cannot save null scene or scene with null ID");
            return;
        }
        vectorMap.put(scene.getId(), embedding);
    }

    @Override
    public void saveBatch(List<Scene> scenes, List<float[]> embeddings) {
        if (scenes.size() != embeddings.size()) {
            throw new IllegalArgumentException("Scenes and embeddings size mismatch");
        }
        for (int i = 0; i < scenes.size(); i++) {
            save(scenes.get(i), embeddings.get(i));
        }
    }

    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        if (topK <= 0) {
            return Collections.emptyList();
        }
        if (vectorMap.isEmpty()) {
            return Collections.emptyList();
        }
        
        if (filter != null && !filter.isEmpty()) {
            log.warn("InMemoryVectorStore does not support filtering yet. Filter ignored: {}", filter);
        }

        // 使用最小堆维护 TopK (按分数升序，堆顶是最小的)
        PriorityQueue<VectorRecord> topKQueue = new PriorityQueue<>(Comparator.comparingDouble(VectorRecord::getScore));

        for (Map.Entry<String, float[]> entry : vectorMap.entrySet()) {
            String id = entry.getKey();
            float[] vector = entry.getValue();
            
            double similarity = cosineSimilarity(queryEmbedding, vector);

            if (topKQueue.size() < topK) {
                topKQueue.offer(new VectorRecord(id, similarity));
            } else {
                if (similarity > topKQueue.peek().getScore()) {
                    topKQueue.poll();
                    topKQueue.offer(new VectorRecord(id, similarity));
                }
            }
        }

        // 导出并按分数降序排列
        List<VectorRecord> results = new ArrayList<>(topKQueue);
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results;
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector dimensions mismatch: " + v1.length + " vs " + v2.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
