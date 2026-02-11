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
    // === RAG 核心字段 ===
    /** 小说名称 */
    private String novel;
    
    /** 版本号 */
    private String version;
    
    /** 章节标题 */
    private String chapterTitle;
    
    /** 章节索引 */
    private Integer chapterIndex;
    
    /** 起始段落 */
    private Integer startParagraph;
    
    /** 结束段落 */
    private Integer endParagraph;
    
    /** Chunk 类型 (e.g., "scene", "summary") */
    private String chunkType;
    
    /** 角色/功能 (e.g., "narration", "dialogue") */
    private String role;

    /** 信息密度得分 */
    private Double densityScore;

    /** 质量得分 (PPL模拟) */
    private Double qualityScore;

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
