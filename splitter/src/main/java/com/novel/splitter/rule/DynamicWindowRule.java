package com.novel.splitter.rule;

import com.novel.splitter.domain.model.SemanticSegment;

import java.util.List;

/**
 * 动态窗口切分规则 (Phase 3)
 * <p>
 * 根据内容密度动态调整目标切分长度。
 * </p>
 */
public class DynamicWindowRule implements SplitRule {

    private static final int BASE_TARGET_LENGTH = 1200;
    private static final int HIGH_DENSITY_TARGET = 800;  // 高密度（代码、公式）
    private static final int LOW_DENSITY_TARGET = 1500;  // 低密度（对话、流水账）
    private static final int ABSOLUTE_MAX_LENGTH = 3000;

    @Override
    public Decision evaluate(int currentLength, List<SemanticSegment> currentBuffer, SemanticSegment nextSegment) {
        // 1. 强制限制：绝对最大长度
        if (currentLength >= ABSOLUTE_MAX_LENGTH) {
            return Decision.MUST_SPLIT;
        }

        // 2. 计算动态目标长度
        int dynamicTarget = calculateDynamicTarget(currentBuffer);

        // 3. 目标判定
        if (currentLength >= dynamicTarget) {
             return Decision.CAN_SPLIT;
        }

        return Decision.NO_SPLIT;
    }

    private int calculateDynamicTarget(List<SemanticSegment> buffer) {
        if (buffer == null || buffer.isEmpty()) return BASE_TARGET_LENGTH;

        // 密度分析 (Density Analysis)
        
        // 判据 1: 是否包含代码块 -> 高密度
        boolean hasCode = buffer.stream().anyMatch(s -> "CODE_BLOCK".equals(s.getType()));
        if (hasCode) return HIGH_DENSITY_TARGET;

        // 判据 2: 对话比例 -> 低密度
        // 统计 SemanticSegment 中类型为 DIALOGUE 的比例
        long dialogueCount = buffer.stream().filter(s -> "DIALOGUE".equals(s.getType())).count();
        double dialogueRatio = (double) dialogueCount / buffer.size();
        
        if (dialogueRatio > 0.5) {
            return LOW_DENSITY_TARGET;
        }

        return BASE_TARGET_LENGTH;
    }
}
