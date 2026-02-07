package com.novel.splitter.domain.model.embedding.chroma;

import lombok.Data;
import java.util.List;

/**
 * ChromaDB 查询响应结果
 * <p>
 * 对应 ChromaDB query API 的 JSON 响应结构。
 * 注意：ChromaDB 支持批量查询，因此字段通常是二维列表。
 * </p>
 */
@Data
public class ChromaQueryResponse {
    /** 
     * 匹配的文档 ID 列表
     * <p>外层 List 对应查询的 Batch，内层 List 对应 TopK 结果</p>
     */
    private List<List<String>> ids;
    
    /** 
     * 匹配的文档距离列表 (Distance)
     */
    private List<List<Double>> distances;
    
    /** 
     * 匹配的文档元数据列表
     */
    private List<List<Object>> metadatas;
}
