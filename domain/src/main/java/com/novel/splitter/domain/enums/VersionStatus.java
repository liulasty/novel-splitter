package com.novel.splitter.domain.enums;

/**
 * 小说版本（NovelVersion）的切分与向量化生命周期状态。
 */
public enum VersionStatus {
    /** 待处理 */
    PENDING,
    /** 场景切分中 */
    SPLITTING,
    /** 场景切分完成 */
    SPLIT_DONE,
    /** 向量化中 */
    EMBEDDING,
    /** 向量化完成 */
    EMBED_DONE,
    /** 已激活（可检索） */
    ACTIVE,
    /** 失败 */
    FAILED,
    /** 已废弃 */
    ABANDONED
}
