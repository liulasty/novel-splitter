package com.novel.splitter.embedding.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;
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
    private static final String METADATA_FILE = "vector_metadata.json";
    private final Map<String, float[]> vectorMap = new ConcurrentHashMap<>();
    private final Map<String, com.novel.splitter.domain.model.SceneMetadata> metadataMap = new ConcurrentHashMap<>();
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

        File metaFile = new File(METADATA_FILE);
        if (metaFile.exists()) {
            try {
                Map<String, com.novel.splitter.domain.model.SceneMetadata> loadedMeta = objectMapper.readValue(metaFile, new TypeReference<Map<String, com.novel.splitter.domain.model.SceneMetadata>>() {});
                metadataMap.putAll(loadedMeta);
                log.info("Loaded {} metadata entries from {}", metadataMap.size(), METADATA_FILE);
            } catch (IOException e) {
                log.error("Failed to load metadata store from file", e);
            }
        }
    }

    @PreDestroy
    public void persist() {
        try {
            objectMapper.writeValue(new File(STORE_FILE), vectorMap);
            log.info("Persisted {} vectors to {}", vectorMap.size(), STORE_FILE);
            
            objectMapper.writeValue(new File(METADATA_FILE), metadataMap);
            log.info("Persisted {} metadata entries to {}", metadataMap.size(), METADATA_FILE);
        } catch (IOException e) {
            log.error("Failed to persist vector store", e);
        }
    }

    /**
     * 清空存储 (用于测试)
     */
    @Override
    public void reset() {
        vectorMap.clear();
        metadataMap.clear();
        log.info("Vector store cleared.");
    }

    @Override
    public long count() {
        return vectorMap.size();
    }

    @Override
    public void delete(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }
        
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, com.novel.splitter.domain.model.SceneMetadata> entry : metadataMap.entrySet()) {
            String id = entry.getKey();
            com.novel.splitter.domain.model.SceneMetadata meta = entry.getValue();
            
            boolean match = true;
            for (Map.Entry<String, Object> f : filter.entrySet()) {
                String key = f.getKey();
                Object expected = f.getValue();
                Object actual = null;
                
                if ("novel".equals(key)) actual = meta.getNovel();
                else if ("version".equals(key)) actual = meta.getVersion();
                
                if (!Objects.equals(actual, expected)) {
                    match = false;
                    break;
                }
            }
            
            if (match) {
                toRemove.add(id);
            }
        }
        
        for (String id : toRemove) {
            vectorMap.remove(id);
            metadataMap.remove(id);
        }
        log.info("Deleted {} vectors matching filter {}", toRemove.size(), filter);
    }

    @Override
    public void save(Scene scene, float[] embedding) {
        if (scene == null || scene.getId() == null) {
            log.warn("Cannot save null scene or scene with null ID");
            return;
        }
        vectorMap.put(scene.getId(), embedding);
        if (scene.getMetadata() != null) {
            metadataMap.put(scene.getId(), scene.getMetadata());
        }
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

        // 使用最小堆维护 TopK (按分数升序，堆顶是最小的)
        PriorityQueue<VectorRecord> topKQueue = new PriorityQueue<>(Comparator.comparingDouble(VectorRecord::getScore));

        for (Map.Entry<String, float[]> entry : vectorMap.entrySet()) {
            String id = entry.getKey();
            
            // Filter Logic
            if (filter != null && !filter.isEmpty()) {
                com.novel.splitter.domain.model.SceneMetadata meta = metadataMap.get(id);
                if (meta == null) {
                    // Metadata missing but filter required -> skip
                    continue;
                }
                
                boolean match = true;
                for (Map.Entry<String, Object> f : filter.entrySet()) {
                    String key = f.getKey();
                    Object expected = f.getValue();
                    Object actual = null;
                    
                    // Simple field mapping
                    if ("novel".equals(key)) actual = meta.getNovel();
                    else if ("version".equals(key)) actual = meta.getVersion();
                    
                    if (!Objects.equals(actual, expected)) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue;
            }

            float[] vector = entry.getValue();
            
            double similarity = cosineSimilarity(queryEmbedding, vector);

            com.novel.splitter.domain.model.SceneMetadata meta = metadataMap.get(id);
            Map<String, Object> metaMap = new HashMap<>();
            if (meta != null) {
                if (meta.getNovel() != null) metaMap.put("novel", meta.getNovel());
                if (meta.getVersion() != null) metaMap.put("version", meta.getVersion());
            }

            if (topKQueue.size() < topK) {
                topKQueue.offer(new VectorRecord(id, similarity, metaMap));
            } else if (similarity > topKQueue.peek().getScore()) {
                topKQueue.poll();
                topKQueue.offer(new VectorRecord(id, similarity, metaMap));
            }
        }

        List<VectorRecord> results = new ArrayList<>(topKQueue);
        results.sort(Comparator.comparingDouble(VectorRecord::getScore).reversed());
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
