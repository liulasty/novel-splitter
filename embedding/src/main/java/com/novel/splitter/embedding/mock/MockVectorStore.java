package com.novel.splitter.embedding.mock;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock 向量存储
 * <p>
 * 简单的内存存储，用于验证流程。
 * </p>
 */
@Slf4j
public class MockVectorStore implements VectorStore {

    // 内存索引：Scene ID -> Vector
    private final Map<String, float[]> index = new ConcurrentHashMap<>();
    
    // 为了 Mock 搜索返回，我们需要知道 ID 列表
    private final List<String> ids = new ArrayList<>();

    @Override
    public void delete(Map<String, Object> filter) {
        // Mock delete
    }

    @Override
    public void reset() {
        index.clear();
        ids.clear();
    }

    @Override
    public long count() {
        return index.size();
    }

    // 清空数据，用于测试隔离
    public void clear() {
        index.clear();
        ids.clear();
    }

    @Override
    public void save(Scene scene, float[] embedding) {
        log.debug("Mock saving scene: {} (Vector dim: {})", scene.getId(), embedding.length);
        index.put(scene.getId(), embedding);
        ids.add(scene.getId());
    }

    @Override
    public void saveBatch(List<Scene> scenes, List<float[]> embeddings) {
        log.info("Mock saving batch of {} scenes", scenes.size());
        for (int i = 0; i < scenes.size(); i++) {
            save(scenes.get(i), embeddings.get(i));
        }
    }

    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        log.info("Mock search with topK={}, filter={}", topK, filter);
        // 简单 Mock：直接返回前 K 个已存储的 ID，分数随机
        List<VectorRecord> results = new ArrayList<>();
        int limit = Math.min(topK, ids.size());
        
        for (int i = 0; i < limit; i++) {
            results.add(new VectorRecord(ids.get(i), 0.9 - (i * 0.1)));
        }
        return results;
    }
}
