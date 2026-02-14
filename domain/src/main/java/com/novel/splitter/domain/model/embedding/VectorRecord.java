package com.novel.splitter.domain.model.embedding;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量记录
 * <p>
 * VectorStore 返回的中间对象，仅包含 ID 和 分数，不包含完整内容。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecord {
    /** Scene ID */
    private String chunkId;
    
    /** 相似度分数 */
    private double score;

    /** 元数据 */
    private java.util.Map<String, Object> metadata;
}
