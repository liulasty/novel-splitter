package com.novel.splitter.domain.model.embedding.chroma;

import lombok.Data;

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
}
