package com.novel.splitter.domain.enums;

/**
 * 版本化场景切分策略。
 */
public enum SplitStrategy {
    /** 场景边界切分：按对话/叙事段落边界切分场景 */
    SCENE_BOUNDARY,
    /** 滑窗重叠切分：固定窗口 + 重叠片段 */
    OVERLAP_CHUNK,
    /** 语义切分：基于语义段构建 */
    SEMANTIC
}
