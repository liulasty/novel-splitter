package com.novel.splitter.application.model.dto;

import lombok.Data;

/**
 * 强制按原文重跑章节解析；可选自定义章节标题正则（整行匹配）。
 */
@Data
public class ReparseChaptersRequestDto {
    private String version;
    /** 可选；非空时覆盖默认章节标题规则 */
    private String chapterTitleRegex;
    /** 识别策略：PLAIN / VOLUME_CHAPTER / CUSTOM；默认 PLAIN */
    private String strategy;
    private int maxScenes;
}
