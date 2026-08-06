package com.novel.splitter.retrieval.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RAG（检索增强生成）请求参数数据传输对象 (DTO)
 * <p>
 * 封装了前端或外部系统向 RAG 接口发起问答请求时所需的各项参数。
 * 包含必填的用户问题以及可选的检索和过滤配置。
 * </p>
 */
@Data
public class RagRequest {
    /** 
     * 用户提出的自然语言问题 
     * // 该字段为必填项，作为向量检索的核心查询内容以及最终发送给 LLM 的输入 
     */
    @NotBlank(message = "问题不能为空")
    private String question;
    
    /** 
     * 检索返回的最相关片段数量 
     * // 默认值为 3，用于控制召回的小说片段数量，直接影响上下文的丰富度和 Token 消耗 
     */
    @Min(value = 1, message = "topK 必须大于 0")
    private int topK = 3;

    /**
     * 目标小说 ID（与 Chroma / DB 中 metadata.novelId 一致）。
     */
    private String novelId;

    /**
     * 数据版本
     * // 用于指定小说数据解析和入库时的版本（如 v1, v2），保证数据的一致性
     */
    private String version;

    /** 与场景/向量分区一致；多数据集共用同一 version 时必填 */
    private Integer chunkSize;
    private Integer chunkOverlap;

    /** 上下文场景数上限（覆盖服务端默认），≤0 表示使用服务端默认 */
    private Integer maxScenes;
    /** 上下文 Token 预算（覆盖服务端默认），≤0 表示使用服务端默认 */
    private Integer maxContextTokens;
    /** 回答目标 Token 数，≤0 表示不限制；会影响提示词中的输出长度约束 */
    private Integer maxAnswerTokens;

    /** 按出场人物过滤（需 retrieval.structured-filter.enabled 打开） */
    private String characterFilter;

    /** 按故事地点过滤 */
    private String locationFilter;

    /** 按故事时间过滤 */
    private String timeFilter;
}
