package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.validation.core.SemanticSegmentBuilder;

/**
 * 段落相关性评分器
 * 根据上下文（尤其是说话人和动作的关系）评估两个段落是否应该合并。
 */
public class ParagraphRelevanceScorer {

    private final SpeakerModel speakerModel;

    public ParagraphRelevanceScorer(SpeakerModel speakerModel) {
        this.speakerModel = speakerModel;
    }

    /**
     * 判断前一个段落和当前段落是否具有强相关性，可以合并
     */
    public boolean shouldMerge(RawParagraph prev, RawParagraph curr, String prevType, String currType) {
        // 1. 锚点不参与吸附合并
        if (prev.isAnchor() || curr.isAnchor()) {
            return false;
        }

        // 2. 叙述 (前缀) + 对话
        if (SemanticSegmentBuilder.TYPE_NARRATION.equals(prevType) && SemanticSegmentBuilder.TYPE_DIALOGUE.equals(currType)) {
            if (speakerModel.isSpeakerAction(prev)) {
                return true;
            }
        }

        // 3. 对话 + 叙述 (后缀)
        if (SemanticSegmentBuilder.TYPE_DIALOGUE.equals(prevType) && SemanticSegmentBuilder.TYPE_NARRATION.equals(currType)) {
            if (speakerModel.isSpeakerAction(curr)) {
                return true;
            }
        }

        return false;
    }
}
