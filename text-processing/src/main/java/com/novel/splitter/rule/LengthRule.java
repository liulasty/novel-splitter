package com.novel.splitter.rule;

import com.novel.splitter.domain.model.SemanticSegment;

import java.util.List;

public class LengthRule implements SplitRule {

    private final int targetLength;
    private final int maxLength;

    public LengthRule(int targetLength, int maxLength) {
        this.targetLength = targetLength;
        this.maxLength = maxLength;
    }

    @Override
    public Decision evaluate(int currentLength, List<SemanticSegment> currentBuffer, SemanticSegment nextSegment) {
        // 1. 强制限制：如果加上下一段会超过最大长度，且当前已有内容，则必须切分
        // 注意：这里是一个简单的预判。更严格的逻辑可能需要看 nextSegment 是否巨大。
        // 但 SemanticSegmentBuilder 已经限制了单个 Segment 的大小（如800字）。
        if (currentLength >= maxLength) {
            return Decision.MUST_SPLIT;
        }

        // 2. 目标限制：达到目标长度，建议切分
        if (currentLength >= targetLength) {
            return Decision.CAN_SPLIT;
        }

        // 3. 否则继续积累
        return Decision.NO_SPLIT;
    }
}
