package com.novel.splitter.embedding.api;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;

import java.util.List;

/**
 * 向量存储接口 (Vector Store)
 * <p>
 * 在 NLP/RAG（Retrieval-Augmented Generation）小说处理系统中，负责将拆分后的小说场景 (Scene) 
 * 及其对应的高维向量 (Embedding) 存储到向量数据库（如 Milvus, Qdrant, Pinecone 等）中，
 * 并提供基于语义的相似度检索能力，从而为生成式问答或上下文补全提供精准的上下文信息。
 * </p>
 */
public interface VectorStore {

    /**
     * 保存单条场景及其对应的向量
     * <p>
     * 将给定的小说场景模型和嵌入模型生成的向量特征持久化到向量库中。
     * 通常会将场景的 ID、所属章节等关键信息作为元数据 (Metadata) 随向量一同保存，以便后续进行过滤查询。
     * </p>
     *
     * @param scene     小说场景对象，包含场景的具体文本、ID 及元数据等
     * @param embedding 由 EmbeddingService 针对该场景生成的浮点数组向量
     */
    void save(Scene scene, float[] embedding);

    /**
     * 批量保存场景及其对应的向量
     * <p>
     * 在系统初始化或大批量文档入库时使用，将多个场景及向量打包执行写入操作，
     * 从而减少网络请求开销，显著提高数据入库效率。
     * </p>
     * 
     * @param scenes     需要保存的小说场景对象列表
     * @param embeddings 对应场景的浮点数组向量列表（必须与 scenes 列表长度及顺序一致）
     */
    void saveBatch(List<Scene> scenes, List<float[]> embeddings);

    /**
     * 语义相似度检索 (Semantic Search)
     * <p>
     * 根据用户提问（或查询上下文）生成的向量，在向量库中寻找最相似（如余弦相似度最高）的 topK 个场景。
     * 支持传入元数据过滤条件，以便在特定的章节或角色范围内进行检索。
     * </p>
     *
     * @param queryEmbedding 代表用户查询意图的浮点数组查询向量
     * @param topK           需要返回的最高相似度结果的最大数量
     * @param filter         元数据过滤条件映射 (Key -> Value)，例如：{"chapterId": "123"}。用于精确控制检索范围
     * @return 匹配的向量记录列表，每条记录包含向量库中的主键 ID 及相似度分数 (Score)
     */
    List<VectorRecord> search(float[] queryEmbedding, int topK, java.util.Map<String, Object> filter);

    /**
     * 语义相似度检索 (无元数据过滤)
     * <p>
     * 在整个向量库范围内，根据查询向量寻找最相似的 topK 个场景记录。
     * 这是 {@link #search(float[], int, java.util.Map)} 的重载便捷方法。
     * </p>
     *
     * @param queryEmbedding 代表用户查询意图的浮点数组查询向量
     * @param topK           需要返回的最高相似度结果的最大数量
     * @return 匹配的向量记录列表，包含匹配的 ID 及相似度分数 (Score)
     */
    default List<VectorRecord> search(float[] queryEmbedding, int topK) {
        // 调用包含 filter 参数的 search 方法，传入空映射表示不使用任何元数据过滤条件
        return search(queryEmbedding, topK, java.util.Collections.emptyMap());
    }

    /**
     * 根据元数据过滤条件删除对应的向量记录
     * <p>
     * 当小说的某个章节被更新或删除时，可使用此方法将关联的旧场景向量数据从向量库中清理掉。
     * </p>
     *
     * @param filter 用于定位需要删除的记录的过滤条件 (Key -> Value)，例如：{"novelId": "456"}
     * @throws IllegalStateException 底层向量库删除失败时（如 Chroma 不可用或非幂等清理不可接受时）抛出
     */
    void delete(java.util.Map<String, Object> filter);

    /**
     * 清空整个向量存储库
     * <p>
     * 这是一个高危操作。通常在重置整个 RAG 知识库或清空测试环境数据时调用。
     * 调用后会删除所有的向量及元数据记录。
     * </p>
     */
    void reset();

    /**
     * 获取向量存储库中的总记录数
     * <p>
     * 用于统计和监控当前向量数据库中已保存的小说场景总数量。
     * </p>
     *
     * @return 当前存储库中向量记录的总条数
     */
    long count();
}
