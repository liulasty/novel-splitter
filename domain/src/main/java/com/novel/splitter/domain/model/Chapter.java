package com.novel.splitter.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 章节
 * <p>
 * 表示小说的一个章节，包含标题和该章节对应的段落范围。
 * </p>
 */
@Getter
@Builder
@ToString
public class Chapter {
    /**
     * 章节序号，从 1 开始
     */
    private final int index;

    /**
     * 章节标题
     */
    private final String title;

    /**
     * 起始段落索引（包含）
     */
    private final int startParagraphIndex;

    /**
     * 结束段落索引（包含）
     */
    private final int endParagraphIndex;
}
