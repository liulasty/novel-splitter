package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.validation.core.SemanticSegmentBuilder;

/**
 * 段落相关性评分器
 * <p>
 * 该类主要用于在 NLP/RAG 系统的文本处理阶段，根据上下文特征
 * （特别是说话人实体及其关联的动作）来评估相邻的两个段落是否具有强相关性，
 * 从而决定它们是否应当在语义层面上被合并为一个整体段落。
 * </p>
 */
public class ParagraphRelevanceScorer {

    /**
     * 说话人模型，用于判断段落中是否包含说话人的具体动作特征
     */
    private final SpeakerModel speakerModel;

    /**
     * 构造函数
     *
     * @param speakerModel 说话人模型实例，提供对段落进行动作判断的能力
     */
    public ParagraphRelevanceScorer(SpeakerModel speakerModel) {
        this.speakerModel = speakerModel;
    }

    /**
     * 判断前一个段落和当前段落是否具有强相关性，从而决定是否可以合并。
     *
     * @param prev     前一个原始段落对象
     * @param curr     当前原始段落对象
     * @param prevType 前一个段落的语义类型（例如：叙述、对话等）
     * @param currType 当前段落的语义类型（例如：叙述、对话等）
     * @return 如果两个段落具有强相关性且符合合并条件，则返回 true；否则返回 false
     */
    public boolean shouldMerge(RawParagraph prev, RawParagraph curr, String prevType, String currType) {
        // 1. 检查锚点：如果任一段落被标记为锚点（通常代表场景切换或不可拆分/合并的关键节点），则直接拒绝合并
        if (prev.isAnchor() || curr.isAnchor()) {
            return false;
        }

        // 2. 模式一：前置叙述与后置对话的组合
        // 如果前一个段落是叙述类型，且当前段落是对话类型
        if (SemanticSegmentBuilder.TYPE_NARRATION.equals(prevType) && SemanticSegmentBuilder.TYPE_DIALOGUE.equals(currType)) {
            // 判断前一个叙述段落中是否包含引导该对话的说话人动作（例如：“他转过身说：”）
            if (speakerModel.isSpeakerAction(prev)) {
                return true;
            }
        }

        // 3. 模式二：前置对话与后置叙述的组合
        // 如果前一个段落是对话类型，且当前段落是叙述类型
        if (SemanticSegmentBuilder.TYPE_DIALOGUE.equals(prevType) && SemanticSegmentBuilder.TYPE_NARRATION.equals(currType)) {
            // 判断当前叙述段落是否包含补充说明该对话的说话人动作（例如：“他边说边笑了起来。”）
            if (speakerModel.isSpeakerAction(curr)) {
                return true;
            }
        }

        // 默认情况下，如果不满足上述任何强相关模式，则认为不应合并
        return false;
    }
}
