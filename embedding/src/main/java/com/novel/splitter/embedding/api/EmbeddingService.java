package com.novel.splitter.embedding.api;

import java.util.List;

/**
 * 嵌入服务接口
 * <p>
 * 负责将文本转换为向量 (Vector / Embedding)。
 * </p>
 */
public interface EmbeddingService {

    /**
     * 单文本嵌入
     *
     * @param text 文本内容
     * @return 向量数据 (float 数组)
     */
    float[] embed(String text);

    /**
     * 批量文本嵌入
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    List<float[]> embedBatch(List<String> texts);
}
