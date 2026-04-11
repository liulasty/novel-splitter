package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Scene 元数据
 * <p>
 * 存储关于 Scene 的辅助信息，符合 RAG Chunk 标准形态。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneMetadata {

    /** 尚未写入真实分析结果时的哨兵值（与 0~1 有效得分区分） */
    public static final double SCORE_NOT_COMPUTED = -1.0;

    // === RAG 核心字段 ===
    /** 小说名称 */
    private String novel;
    
    /** 版本号 */
    private String version;

    /** 场景切分时生效的滑窗块大小（字符），与 DB scenes.chunk_size 一致 */
    private Integer chunkSize;

    /** 场景切分时生效的块重叠（字符），与 DB scenes.chunk_overlap 一致 */
    private Integer chunkOverlap;
    
    /** 章节标题 */
    private String chapterTitle;
    
    /** 章节索引 */
    private Integer chapterIndex;
    
    /** 起始段落 */
    private Integer startParagraph;
    
    /** 结束段落 */
    private Integer endParagraph;

    /** 序列号（章内从 0 递增） */
    private Integer sequenceNum;

    /** 角色/功能，预留由 LLM 抽取 */
    private String role;

    /** 信息密度得分；未计算时为 {@link #SCORE_NOT_COMPUTED} */
    @Builder.Default
    private Double densityScore = SCORE_NOT_COMPUTED;

    /** 质量得分 (PPL模拟)；未计算时为 {@link #SCORE_NOT_COMPUTED} */
    @Builder.Default
    private Double qualityScore = SCORE_NOT_COMPUTED;

    // === 语义分析字段 (预留) ===
    /** 出现的人物列表 */
    private List<String> characters;

    /** 场景地点 */
    private String location;

    /** 时间信息 */
    private String time;

    /** 扩展字段 */
    private Map<String, Object> extra;
}
