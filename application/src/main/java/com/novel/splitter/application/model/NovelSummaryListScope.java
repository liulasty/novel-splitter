package com.novel.splitter.application.model;

/**
 * 小说摘要列表的查询范围（供各页面选用不同语义，避免一律扫全表）。
 */
public enum NovelSummaryListScope {
    /** 全部未软删的 DB 记录（入库/运维/知识库总览） */
    ALL,
    /** 已向量化完成，可用于 RAG 检索与对话（与 {@code NovelStatus.COMPLETED} 一致） */
    EMBED_READY
}
