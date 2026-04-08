package com.novel.splitter.rule;

import com.novel.splitter.domain.model.SemanticSegment;

import java.util.List;

/**
 * 基础长度切分规则
 * <p>
 * 基于固定字符长度阈值进行文本切分的规则实现。在 NLP/RAG 预处理阶段，
 * 确保文本块不会过大以适应模型的上下文窗口，同时达到目标长度时推荐切分。
 * </p>
 */
public class LengthRule implements SplitRule {

    /** 目标切分长度，达到该长度时表示可以进行切分 */
    private final int targetLength;
    /** 最大允许长度，达到或超过该长度时必须强制进行切分 */
    private final int maxLength;

    /**
     * 构造长度切分规则。
     *
     * @param targetLength 期望的目标切分长度
     * @param maxLength    允许的最大文本块长度
     */
    public LengthRule(int targetLength, int maxLength) {
        this.targetLength = targetLength;
        this.maxLength = maxLength;
    }

    /**
     * 根据当前累积的文本长度评估是否需要进行切分。
     *
     * @param currentLength 当前 Scene 或文本块已累积的字数
     * @param currentBuffer 当前 Scene 已累积的语义段落列表
     * @param nextSegment   下一个即将加入的语义段落
     * @return 切分决策枚举对象 {@link Decision}
     */
    @Override
    public Decision evaluate(int currentLength, List<SemanticSegment> currentBuffer, SemanticSegment nextSegment) {
        // 1. 强制限制检查：如果当前积累的内容已经达到或超过了最大长度限制，则必须进行切分
        // 注意：这里是一个简单的预判。更严格的逻辑可能需要看 nextSegment 的大小是否巨大。
        // 但在系统中，SemanticSegmentBuilder 已经对单个 Segment 的大小（如800字）进行了限制。
        if (currentLength >= maxLength) {
            return Decision.MUST_SPLIT;
        }

        // 2. 目标限制检查：如果当前积累的内容达到了设定的目标长度，建议在此处切分
        if (currentLength >= targetLength) {
            return Decision.CAN_SPLIT;
        }

        // 3. 否则，当前长度还不足以触发切分，继续积累后续段落
        return Decision.NO_SPLIT;
    }
}
