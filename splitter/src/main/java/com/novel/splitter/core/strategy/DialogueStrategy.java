package com.novel.splitter.core.strategy;

import com.novel.splitter.domain.model.RawParagraph;

/**
 * 对话识别策略
 */
public interface DialogueStrategy {
    /**
     * 探测段落的语义类型
     *
     * @param paragraph 当前段落
     * @param previousType 前一段落类型
     * @return 语义类型（如 DIALOGUE, NARRATION 等）
     */
    String detectType(RawParagraph paragraph, String previousType);
}
