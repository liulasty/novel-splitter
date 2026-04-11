package com.novel.splitter.domain.enums;

public enum NovelStatus {
    PENDING,
    /** 原文已解析为章节结构（chapters + parsed JSON），尚未场景切分 */
    PARSED,
    SPLITTING,
    SPLIT_COMPLETED,
    EMBEDDING,
    COMPLETED,
    FAILED
}
