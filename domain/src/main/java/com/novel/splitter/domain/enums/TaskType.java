package com.novel.splitter.domain.enums;

public enum TaskType {
    /** 仅解析原文：生成 parsed JSON + chapters，不进入切分队列 */
    LOAD,
    SPLIT,
    /** SPLIT 且将串联 EMBED（任务记录类型，便于运维区分） */
    PIPELINE,
    EMBED
}
