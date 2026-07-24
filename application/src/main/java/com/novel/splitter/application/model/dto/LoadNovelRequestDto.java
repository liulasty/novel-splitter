package com.novel.splitter.application.model.dto;

import lombok.Data;

/**
 * 独立 Load：解析原文为 chapters + parsed JSON。
 */
@Data
public class LoadNovelRequestDto {
    /** 与后续 split/embed 对齐的版本标签，默认 v1 */
    private String version;
    /** true：忽略“已完整结构化”短路，清理后强制重新解析 */
    private boolean force;
    /** 可选：章节标题行 Java 正则（整行匹配） */
    private String chapterTitleRegex;
    /** 识别策略：PLAIN / VOLUME_CHAPTER / CUSTOM；默认 PLAIN */
    private String strategy;
}
