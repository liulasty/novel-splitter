package com.novel.splitter.embedding.api;

import java.util.List;

/**
 * 嵌入服务接口 (Embedding Service)
 * <p>
 * 在 NLP/RAG（Retrieval-Augmented Generation）小说处理系统中，负责将小说的文本内容（如段落、场景或章节）
 * 转换为高维稠密向量 (Vector / Embedding)。这些向量随后可以被存储到向量数据库中，
 * 用于后续的语义检索和相似度计算。
 * </p>
 */
public interface EmbeddingService {

    /**
     * 批量文本嵌入 (强制使用批处理)
     * <p>
     * 将一批小说的文本片段列表并行或批量转换为对应的浮点数组向量列表。
     * 在处理大量文本时，批处理能有效提高吞吐量并充分利用计算资源（如 GPU）。
     * </p>
     *
     * @param texts 需要被向量化处理的文本字符串列表 (例如：小说的多个连续段落)
     * @return 包含对应文本特征的浮点数组向量列表。返回列表的大小和顺序必须与输入参数 texts 保持严格一致。
     */
    List<float[]> embedBatch(List<String> texts);
}
