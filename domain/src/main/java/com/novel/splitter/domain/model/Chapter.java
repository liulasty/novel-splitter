package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

/**
 * 章节
 * <p>
 * 表示小说的一个章节，包含标题和该章节对应的段落范围。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class Chapter {
    private Long id;
    
    private String novelId;

    /**
     * 章节序号，从 1 开始
     */
    private int index;

    /**
     * 章节标题
     */
    private String title;

    /**
     * 起始段落索引（包含）
     */
    private int startParagraphIndex;

    /**
     * 结束段落索引（包含）
     */
    private int endParagraphIndex;

    /**
     * 字数
     */
    private int wordCount;
}
