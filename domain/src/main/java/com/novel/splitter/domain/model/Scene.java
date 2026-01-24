package com.novel.splitter.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 场景 (Scene)
 * <p>
 * 系统的核心产出物，代表一个相对独立的、语义完整的文本块。
 * 通常包含 500~2000 字。
 * </p>
 */
@Getter
@Builder
@ToString
public class Scene {
    /**
     * 唯一标识 (UUID 或 Hash)
     */
    private final String id;

    /**
     * 所属章节标题
     */
    private final String chapterTitle;

    /**
     * 所属章节索引
     */
    private final int chapterIndex;

    /**
     * 起始段落索引（全局 RawParagraph index）
     */
    private final int startParagraphIndex;

    /**
     * 结束段落索引（全局 RawParagraph index）
     */
    private final int endParagraphIndex;

    /**
     * 完整文本内容
     */
    private final String text;

    /**
     * 字数
     */
    private final int wordCount;
    
    /**
     * 是否可再切分
     * <p>如果是长文本，建议为 true；如果已经很短，为 false。</p>
     */
    private final boolean canSplit;

    /**
     * 元数据
     */
    private final SceneMetadata metadata;
}
