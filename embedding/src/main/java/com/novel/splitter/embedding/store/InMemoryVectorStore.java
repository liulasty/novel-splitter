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
 * 内存向量存储实现。
 * <p>
 * 提供轻量级的内存向量存储和基于余弦相似度的检索功能，主要用于本地开发测试或轻量级 NLP/RAG 任务。
 * 支持在应用启动时从本地 JSON 文件加载数据，以及在应用关闭时持久化到本地文件。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "embedding.store.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {

    private static final String STORE_FILE = "vector_store.json";
    private static final String METADATA_FILE = "vector_metadata.json";
    
    // 存储向量数据的并发哈希表 (Scene ID -> 向量数组)
    private final Map<String, float[]> vectorMap = new ConcurrentHashMap<>();
    
    // 存储场景元数据的并发哈希表 (Scene ID -> 场景元数据)
    private final Map<String, com.novel.splitter.domain.model.SceneMetadata> metadataMap = new ConcurrentHashMap<>();
    
    // 用于序列化和反序列化 JSON 的工具对象
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 在 Bean 初始化后自动执行的加载方法。
     * 尝试从本地文件中读取之前持久化的向量数据和元数据。
     */
    @PostConstruct
    public void load() {
        File file = new File(STORE_FILE);
        // 如果向量数据文件存在，则读取并加载到内存
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
        // 如果元数据文件存在，则读取并加载到内存
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

    /**
     * 在 Bean 销毁前自动执行的持久化方法。
     * 将当前内存中的向量数据和元数据写入本地 JSON 文件中保存。
     */
    @PreDestroy
    public void persist() {
        try {
            // 将向量数据序列化到文件
            objectMapper.writeValue(new File(STORE_FILE), vectorMap);
            log.info("Persisted {} vectors to {}", vectorMap.size(), STORE_FILE);
            
            // 将元数据序列化到文件
            objectMapper.writeValue(new File(METADATA_FILE), metadataMap);
            log.info("Persisted {} metadata entries to {}", metadataMap.size(), METADATA_FILE);
        } catch (IOException e) {
            log.error("Failed to persist vector store", e);
        }
    }

    /**
     * 清空存储内容。
     * 主要用于测试或重置系统状态。
     */
    @Override
    public void reset() {
        // 清空内存中的映射表
        vectorMap.clear();
        metadataMap.clear();
        log.info("Vector store cleared.");
    }

    /**
     * 获取当前存储的向量总数。
     *
     * @return 向量总数
     */
    @Override
    public long count() {
        return vectorMap.size();
    }

    /**
     * 根据给定的过滤条件删除对应的向量和元数据记录。
     *
     * @param filter 过滤条件映射表
     */
    @Override
    public void delete(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return; // 过滤条件为空时不执行任何操作，避免误删
        }
        
        List<String> toRemove = new ArrayList<>();
        
        // 遍历所有元数据，查找符合过滤条件的记录
        for (Map.Entry<String, com.novel.splitter.domain.model.SceneMetadata> entry : metadataMap.entrySet()) {
            String id = entry.getKey();
            com.novel.splitter.domain.model.SceneMetadata meta = entry.getValue();
            
            boolean match = true;
            // 逐个校验过滤条件
            for (Map.Entry<String, Object> f : filter.entrySet()) {
                String key = f.getKey();
                Object expected = f.getValue();
                Object actual = null;
                
                // 简单的字段映射：目前仅支持对 novel 和 version 字段的过滤
                if ("novelId".equals(key) || "novel".equals(key)) actual = meta.getNovel();
                else if ("version".equals(key)) actual = meta.getVersion();
                else if ("chunkSize".equals(key)) actual = meta.getChunkSize();
                else if ("chunkOverlap".equals(key)) actual = meta.getChunkOverlap();
                
                if (!Objects.equals(actual, expected)) {
                    match = false;
                    break;
                }
            }
            
            // 如果所有条件均满足，则加入待删除列表
            if (match) {
                toRemove.add(id);
            }
        }
        
        // 从内存中移除符合条件的向量及元数据
        for (String id : toRemove) {
            vectorMap.remove(id);
            metadataMap.remove(id);
        }
        log.info("Deleted {} vectors matching filter {}", toRemove.size(), filter);
    }

    /**
     * 保存单个场景及其特征向量。
     *
     * @param scene     场景对象，必须包含非空的 ID
     * @param embedding 对应的特征向量数组
     */
    @Override
    public void save(Scene scene, float[] embedding) {
        if (scene == null || scene.getId() == null) {
            log.warn("Cannot save null scene or scene with null ID");
            return;
        }
        // 将向量存入哈希表
        vectorMap.put(scene.getId(), embedding);
        // 如果场景附带元数据，也一并保存
        if (scene.getMetadata() != null) {
            metadataMap.put(scene.getId(), scene.getMetadata());
        }
    }

    /**
     * 批量保存场景列表及其对应的特征向量。
     *
     * @param scenes     场景对象列表
     * @param embeddings 对应的特征向量列表
     * @throws IllegalArgumentException 如果场景数量与向量数量不一致
     */
    @Override
    public void saveBatch(List<Scene> scenes, List<float[]> embeddings) {
        if (scenes.size() != embeddings.size()) {
            throw new IllegalArgumentException("Scenes and embeddings size mismatch");
        }
        // 循环逐个保存
        for (int i = 0; i < scenes.size(); i++) {
            save(scenes.get(i), embeddings.get(i));
        }
    }

    /**
     * 在内存中执行近似最近邻搜索（暴力遍历），返回最相似的前 K 个结果。
     *
     * @param queryEmbedding 查询向量
     * @param topK           期望返回的最大结果数
     * @param filter         元数据过滤条件
     * @return 按相似度降序排列的结果记录列表
     */
    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        if (topK <= 0) {
            return Collections.emptyList();
        }
        if (vectorMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 使用最小堆维护 TopK 结果 (按相似度分数升序排列，堆顶是堆中最小的分数)
        PriorityQueue<VectorRecord> topKQueue = new PriorityQueue<>(Comparator.comparingDouble(VectorRecord::getScore));

        // 遍历所有的存储向量
        for (Map.Entry<String, float[]> entry : vectorMap.entrySet()) {
            String id = entry.getKey();
            
            // Filter Logic (过滤逻辑)
            if (filter != null && !filter.isEmpty()) {
                com.novel.splitter.domain.model.SceneMetadata meta = metadataMap.get(id);
                if (meta == null) {
                    // Metadata missing but filter required -> skip
                    // (如果记录缺失元数据，但又要求过滤，则跳过该记录)
                    continue;
                }
                
                boolean match = true;
                for (Map.Entry<String, Object> f : filter.entrySet()) {
                    String key = f.getKey();
                    Object expected = f.getValue();
                    Object actual = null;
                    
                    // Simple field mapping (简单的字段映射，提取实际值用于比较)
                    if ("novelId".equals(key) || "novel".equals(key)) actual = meta.getNovel();
                    else if ("version".equals(key)) actual = meta.getVersion();
                    else if ("chunkSize".equals(key)) actual = meta.getChunkSize();
                    else if ("chunkOverlap".equals(key)) actual = meta.getChunkOverlap();
                    
                    if (!Objects.equals(actual, expected)) {
                        match = false;
                        break;
                    }
                }
                if (!match) continue; // 如果不匹配过滤条件，则跳过计算
            }

            float[] vector = entry.getValue();
            
            // 计算查询向量与当前向量的余弦相似度
            double similarity = cosineSimilarity(queryEmbedding, vector);

            com.novel.splitter.domain.model.SceneMetadata meta = metadataMap.get(id);
            Map<String, Object> metaMap = new HashMap<>();
            // 组装返回结果中的元数据字典
            if (meta != null) {
                if (meta.getNovel() != null) metaMap.put("novelId", meta.getNovel());
                if (meta.getVersion() != null) metaMap.put("version", meta.getVersion());
                if (meta.getChunkSize() != null) metaMap.put("chunkSize", meta.getChunkSize());
                if (meta.getChunkOverlap() != null) metaMap.put("chunkOverlap", meta.getChunkOverlap());
            }

            // 维护最小堆：如果堆未满则直接加入，如果当前相似度大于堆顶元素的相似度，则替换堆顶
            if (topKQueue.size() < topK) {
                topKQueue.offer(new VectorRecord(id, similarity, metaMap));
            } else if (similarity > topKQueue.peek().getScore()) {
                topKQueue.poll();
                topKQueue.offer(new VectorRecord(id, similarity, metaMap));
            }
        }

        // 提取堆中结果并按分数降序排序后返回
        List<VectorRecord> results = new ArrayList<>(topKQueue);
        results.sort(Comparator.comparingDouble(VectorRecord::getScore).reversed());
        return results;
    }

    /**
     * 计算两个向量之间的余弦相似度。
     *
     * @param v1 向量1
     * @param v2 向量2
     * @return 余弦相似度值，范围通常在 [-1.0, 1.0] 之间，值越大表示越相似
     * @throws IllegalArgumentException 如果两个向量维度不一致
     */
    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1.length != v2.length) {
            throw new IllegalArgumentException("Vector dimensions mismatch: " + v1.length + " vs " + v2.length);
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        // 计算点积和各自的平方和
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }

        // 防止除零异常
        if (normA == 0 || normB == 0) {
            return 0.0;
        }

        // 返回点积除以两个向量模长的乘积
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
