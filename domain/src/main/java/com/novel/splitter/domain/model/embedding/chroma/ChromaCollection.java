package com.novel.splitter.domain.model.embedding.chroma;

import lombok.Data;

import java.util.Map;

/**
 * ChromaDB 集合信息
 * <p>
 * 用于接收 ChromaDB API 返回的集合对象。
 * </p>
 */
@Data
public class ChromaCollection {
    /** 
     * 集合唯一标识符 
     */
    private String id;
    
    /** 
     * 集合名称 
     */
    private String name;

    /**
     * 建表时传入的元数据（如 {@code hnsw:space}），创建后不可改。
     */
    private Map<String, Object> metadata;
}
