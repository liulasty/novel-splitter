package com.novel.splitter.core;

import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.validation.core.SemanticSegmentBuilder;
import java.util.List;

/**
 * 语义密度分析器
 * 负责计算文本块的信息密度（如对话比例、代码块比例等），为后续的文本切分和处理提供密度指标参考。
 * 在NLP/RAG系统中，通过分析语义密度，可以动态调整切分窗口，提高检索质量。
 */
public class SemanticDensityAnalyzer {

    /**
     * 计算输入段落列表中对话所占的比例。
     * 
     * @param segments 语义段落列表
     * @return 对话比例，返回 0.0 到 1.0 之间的值。1.0 表示全部为对话段落，0.0 表示没有对话。
     */
    public double calculateDialogueRatio(List<SemanticSegment> segments) {
        // 如果传入的段落列表为空，则直接返回0.0的对话比例
        if (segments == null || segments.isEmpty()) {
            return 0.0;
        }
        // 统计类型为对话的段落数量
        long dialogueCount = segments.stream()
                .filter(s -> SemanticSegmentBuilder.TYPE_DIALOGUE.equals(s.getType()))
                .count();
        // 计算并返回对话段落数量占总段落数量的比例
        return (double) dialogueCount / segments.size();
    }

    /**
     * 计算非对话密度分数 (即叙述或描述的密度)。
     * 
     * @param segments 语义段落列表
     * @return 非对话密度分数，返回 0.0 到 1.0 之间的值。1.0 表示全部为叙述/描述，0.0 表示全部为对话。
     */
    public double calculateDensityScore(List<SemanticSegment> segments) {
        // 密度分数为 1 减去对话比例
        return 1.0 - calculateDialogueRatio(segments);
    }

    /**
     * 判断输入的段落列表中是否包含高密度文本块（例如代码块）。
     * 高密度文本块通常包含密集的信息，在切分时需要特别处理。
     * 
     * @param segments 语义段落列表
     * @return 如果包含高密度文本块则返回 true，否则返回 false。
     */
    public boolean hasHighDensityBlock(List<SemanticSegment> segments) {
        // 如果传入的段落列表为空，则直接返回false
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        // 检查列表中是否有任意一个段落的类型为 "CODE_BLOCK"
        return segments.stream().anyMatch(s -> "CODE_BLOCK".equals(s.getType()));
    }
}
