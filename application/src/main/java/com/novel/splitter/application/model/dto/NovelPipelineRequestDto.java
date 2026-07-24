package com.novel.splitter.application.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class NovelPipelineRequestDto {
    @NotEmpty(message = "stages 不能为空")
    private List<String> stages;
    private String version;
    private int maxScenes = 0;
    /**
     * 切分入口：FULL=读原文→章节结构化→场景切分（经 Load 队列）；CHAPTER_RELOAD=强制重跑章节解析后再场景切分；
     * SCENE_ONLY=跳过 Load，在已有章节/解析产物上按新版本做场景切分（适合同一小说多 version）。
     */
    private String splitEntry;
    /** 场景块大小（字符数），可选 */
    private Integer chunkSize;
    /** 块重叠（字符数），可选，须小于 chunkSize */
    private Integer chunkOverlap;
    /** 章节解析阶段可选：章节标题行 Java 正则（整行匹配） */
    private String chapterTitleRegex;
    /** 识别策略：PLAIN / VOLUME_CHAPTER / CUSTOM；默认 PLAIN */
    private String strategy;
}
