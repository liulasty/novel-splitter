package com.novel.splitter.retrieval.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索查询参数对象 (DTO)
 * <p>
 * 封装了底层向量数据库进行混合检索（相似度搜索 + 元数据过滤）所需的所有条件。
 * 包含了查询文本、范围限制以及其他特定的过滤标签。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalQuery {

    /** 
     * 用户的自然语言问题 
     * // 将被转换为向量，用于在向量空间中与小说片段进行相似度计算 
     */
    private String question;

    /**
     * 目标小说 ID（向量库 metadata 键 novelId）。
     */
    private String novelId;
    
    /** 
     * 数据版本号 
     * // 用于匹配特定版本的数据，避免跨版本数据污染 
     */
    private String version;

    /** 
     * 检索范围的起始章节号 (包含该章节) 
     * // 用于将检索范围限制在小说的特定进度之后，结合 chapterTo 使用 
     */
    private Integer chapterFrom;

    /** 
     * 检索范围的结束章节号 (包含该章节) 
     * // 用于将检索范围限制在小说的特定进度之前，防止剧透或超出阅读范围 
     */
    private Integer chapterTo;

    /** 
     * 文本角色或功能分类 
     * // 例如："narration"（旁白）、"dialogue"（对话），用于针对特定类型的文本进行过滤 
     */
    private String role;

    /** 
     * 返回的最相关结果数量 (Top-K) 
     * // 辅助字段，指定本次检索需要召回的匹配片段数量，默认值为 5 
     */
    @Builder.Default
    private int topK = 5;
}
