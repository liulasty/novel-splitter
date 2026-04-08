package com.novel.splitter.rule;

import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.core.SemanticDensityAnalyzer;

import java.util.List;

/**
 * 动态窗口切分规则 (Phase 3)
 * <p>
 * 该规则在文本处理中根据内容的语义密度来动态调整目标的切分长度，属于小说切分规则集的一部分。
 * 在NLP/RAG中，为了确保段落内容的语义完整性和检索效果，需要根据高密度文本（如代码）
 * 或低密度文本（如长段对话）对切分窗口进行弹性缩放。
 * </p>
 */
public class DynamicWindowRule implements SplitRule {

    private static final int BASE_TARGET_LENGTH = 1200;  // 基础目标切分长度
    private static final int HIGH_DENSITY_TARGET = 800;  // 高密度内容的目标切分长度（例如代码、公式等），切分粒度更细
    private static final int LOW_DENSITY_TARGET = 1500;  // 低密度内容的目标切分长度（例如对话、流水账叙事等），切分粒度更粗
    private static final int ABSOLUTE_MAX_LENGTH = 3000; // 绝对的最大允许切分长度，超过此长度将强制触发切分
    
    private final SemanticDensityAnalyzer densityAnalyzer;

    /**
     * 构造一个新的动态窗口切分规则对象，并初始化其依赖的语义密度分析器。
     */
    public DynamicWindowRule() {
        this.densityAnalyzer = new SemanticDensityAnalyzer();
    }

    /**
     * 评估当前是否应该基于动态窗口的长度规则进行文本切分。
     * 
     * @param currentLength 当前积累的场景或段落总字数
     * @param currentBuffer 当前场景或段落已累积的语义段落列表
     * @param nextSegment   下一个即将被添加进去的语义段落（用于辅助判定，此处未直接使用）
     * @return 切分决策枚举对象 {@link Decision}，表示强制切分、允许切分或不切分。
     */
    @Override
    public Decision evaluate(int currentLength, List<SemanticSegment> currentBuffer, SemanticSegment nextSegment) {
        // 1. 强制限制检查：如果当前积累长度达到了绝对最大长度阈值，则直接返回必须切分的决定
        if (currentLength >= ABSOLUTE_MAX_LENGTH) {
            return Decision.MUST_SPLIT;
        }

        // 2. 根据当前缓冲区的语义密度情况，动态计算出适应的目标切分长度
        int dynamicTarget = calculateDynamicTarget(currentBuffer);

        // 3. 目标判定检查：如果当前积累长度达到了动态计算的目标长度，则返回可以切分的决定
        if (currentLength >= dynamicTarget) {
             return Decision.CAN_SPLIT;
        }

        // 4. 若不满足以上条件，则继续累积文本，不触发切分
        return Decision.NO_SPLIT;
    }

    /**
     * 根据当前累积的文本内容计算出动态的目标切分长度。
     * 
     * @param buffer 当前场景或段落已累积的语义段落列表
     * @return 计算得到的动态目标长度整数值
     */
    private int calculateDynamicTarget(List<SemanticSegment> buffer) {
        // 如果缓冲区为空或尚未包含段落，则返回默认的基础目标长度
        if (buffer == null || buffer.isEmpty()) return BASE_TARGET_LENGTH;

        // 执行密度分析 (Density Analysis) 以调整长度
        
        // 判据 1: 检查是否包含高密度文本块（如代码块） -> 判定为高密度文本，需要缩短切分长度
        if (densityAnalyzer.hasHighDensityBlock(buffer)) {
            return HIGH_DENSITY_TARGET;
        }

        // 判据 2: 计算文本中对话的比例 -> 判定低密度文本，可以延长切分长度
        double dialogueRatio = densityAnalyzer.calculateDialogueRatio(buffer);
        
        // 如果对话所占比例超过 50%，则认为密度较低，放宽切分长度目标
        if (dialogueRatio > 0.5) {
            return LOW_DENSITY_TARGET;
        }

        // 默认情况：返回基础的切分目标长度
        return BASE_TARGET_LENGTH;
    }
}
