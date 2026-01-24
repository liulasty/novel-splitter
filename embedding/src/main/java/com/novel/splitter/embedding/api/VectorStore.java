package com.novel.splitter.embedding.api;

import com.novel.splitter.domain.model.Scene;

import java.util.List;

/**
 * 向量存储接口
 * <p>
 * 负责存储 Scene 及其对应的向量，并提供检索能力。
 * </p>
 */
public interface VectorStore {

    /**
     * 保存场景及其向量
     *
     * @param scene     场景对象
     * @param embedding 对应的向量
     */
    void save(Scene scene, float[] embedding);

    /**
     * 批量保存
     */
    void saveBatch(List<Scene> scenes, List<float[]> embeddings);

    /**
     * 相似度检索 (Semantic Search)
     *
     * @param queryEmbedding 查询向量
     * @param topK           返回结果数量
     * @return 匹配的向量记录列表 (ID + Score)
     */
    List<VectorRecord> search(float[] queryEmbedding, int topK);
}
