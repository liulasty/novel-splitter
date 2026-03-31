package com.novel.splitter.core;

import com.novel.splitter.domain.model.SemanticSegment;

import java.util.List;

/**
 * 语义密度分析器
 * 负责计算文本块的信息密度（如对话比例、代码块比例等）。
 */
public class SemanticDensityAnalyzer {

    /**
     * 计算对话比例
     * 返回 0.0 到 1.0 之间的值。1.0 表示全是对话。
     */
    public double calculateDialogueRatio(List<SemanticSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return 0.0;
        }
        long dialogueCount = segments.stream()
                .filter(s -> SemanticSegmentBuilder.TYPE_DIALOGUE.equals(s.getType()))
                .count();
        return (double) dialogueCount / segments.size();
    }

    /**
     * 计算非对话密度分数 (叙述/描述密度)
     * 返回 0.0 到 1.0 之间的值。1.0 表示全是叙述/描述。
     */
    public double calculateDensityScore(List<SemanticSegment> segments) {
        return 1.0 - calculateDialogueRatio(segments);
    }

    /**
     * 判断是否包含高密度块（如代码）
     */
    public boolean hasHighDensityBlock(List<SemanticSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        return segments.stream().anyMatch(s -> "CODE_BLOCK".equals(s.getType()));
    }
}
