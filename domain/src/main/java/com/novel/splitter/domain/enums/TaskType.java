package com.novel.splitter.domain.enums;

public enum TaskType {
    /** 仅解析原文：生成 parsed JSON + chapters（独立 Load API，与 CHAPTER_PARSE 行为一致：不自动场景切分） */
    LOAD,
    /**
     * 章节解析：原文 → 正则章节边界 → chapters 落库 + parsed JSON（经 Load 队列，完成后不自动投递 Split）。
     */
    CHAPTER_PARSE,
    /**
     * 场景切分：基于已有章节做滑窗场景落库（经 Split 队列）。
     */
    SCENE_SPLIT,
    /**
     * 场景切分完成后自动串联 EMBED（与 SCENE_SPLIT 共用 Split 队列与消费者逻辑）。
     */
    PIPELINE,
    EMBED
}
