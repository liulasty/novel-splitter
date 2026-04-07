package com.novel.splitter.retrieval.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RAG (检索增强生成) 请求参数
 */
@Data
public class RagRequest {
    /** 
     * 用户提出的问题 
     */
    @NotBlank(message = "问题不能为空")
    private String question;
    
    /** 
     * 检索返回的最相关片段数量 (默认 3) 
     */
    @Min(value = 1, message = "topK 必须大于 0")
    private int topK = 3;
    
    /** 
     * 目标小说名称 (用于限定检索范围) 
     */
    private String novel;
    
    /** 
     * 数据版本 (例如: v1, v2) 
     */
    private String version;
}
