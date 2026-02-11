package com.novel.splitter.domain.model;

/**
 * 段落类型枚举
 */
public enum ParagraphType {
    /**
     * 普通文本
     */
    TEXT,
    /**
     * 标题 (#, ##, ...)
     */
    HEADER,
    /**
     * 代码块 (``` ... ```)
     */
    CODE_BLOCK,
    /**
     * 列表项 (-, 1., ...)
     */
    LIST_ITEM,
    /**
     * 引用 (>)
     */
    QUOTE
}
