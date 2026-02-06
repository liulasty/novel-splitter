package com.novel.splitter.rule;

import com.novel.splitter.domain.model.SemanticSegment;

/**
 * 切分规则接口
 */
public interface SplitRule {

    enum Decision {
        MUST_SPLIT, // 必须切分（如遇到强边界）
        CAN_SPLIT,  // 可以切分（如字数达标且语义完整）
        NO_SPLIT    // 不要切分（如对话中，或字数太少）
    }

    /**
     * 评估当前是否应该切分
     *
     * @param currentLength 当前 Scene 已累积的字数
     * @param nextSegment   下一个即将加入的语义段
     * @return 切分决策
     */
    Decision evaluate(int currentLength, SemanticSegment nextSegment);
}
