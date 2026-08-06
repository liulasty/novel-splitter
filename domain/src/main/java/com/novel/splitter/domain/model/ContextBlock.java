package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Prompt 中的上下文块
 * <p>
 * 代表检索到的一个证据单元，包含内容及其元数据。
 * 禁止使用 String 拼接，保持结构化以便于后续处理（如 Token 计算、溯源）。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextBlock {
    /** 原始 Chunk ID (用于溯源和去重) */
    private String chunkId;

    /** 文本内容 */
    private String content;

    /** 关联的元数据 (直接引用 SceneMetadata) */
    private SceneMetadata sceneMetadata;

    /** Token 数量 */
    private int tokenCount;

    /** 排名 (1-based) */
    private int rank;

    /** 评分 */
    private double score;

    /** 扩展元数据 */
    private Map<String, Object> metadata;

    /** 前文上下文（上一场景结尾的重叠文本），用于组装时衔接语义；引用/溯源不显示 */
    private String prefixContext;

    public static final String PREFIX_LEAD = "[上文接续]\n";
    public static final String PREFIX_BODY_SEP = "\n[正文]\n";

    /**
     * 供 LLM 序列化使用的正文：若设置了 prefixContext 则拼上「上文接续 + 分隔符」，
     * 否则返回原始 content。引用/溯源仍用 {@link #getContent()}，保持干净。
     */
    public String effectiveContent() {
        if (prefixContext == null || prefixContext.isBlank()) {
            return content;
        }
        return PREFIX_LEAD + prefixContext + PREFIX_BODY_SEP + content;
    }
}
