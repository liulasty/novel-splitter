package com.novel.splitter.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * 小说 (Novel)
 * <p>
 * 代表一本完整的小说，包含元数据、章节列表和所有原始段落。
 * </p>
 */
@Getter
@Builder
@ToString
public class Novel {
    /**
     * 小说标题
     */
    private final String title;

    /**
     * 作者
     */
    private final String author;

    /**
     * 章节列表
     */
    private final List<Chapter> chapters;

    /**
     * 原始段落列表 (全局)
     */
    private final List<RawParagraph> paragraphs;
}
