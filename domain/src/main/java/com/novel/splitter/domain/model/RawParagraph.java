package com.novel.splitter.domain.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * 原始段落
 * <p>
 * 代表文本文件中的一行物理文本。
 * 通常由 ParagraphSplitter 从原始文件中读取并构建。
 * </p>
 */
@Getter
@Builder
@ToString
public class RawParagraph {
    /**
     * 全局段落索引（行号），从 0 开始
     */
    private final int index;

    /**
     * 文本内容（已去除首尾空白）
     */
    private final String content;

    /**
     * 是否为空行
     */
    private final boolean isEmpty;
}
