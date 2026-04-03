package com.novel.splitter.validation.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.validation.core.strategy.DialogueStrategy;
import com.novel.splitter.validation.core.strategy.SegmentMergeStrategy;
import com.novel.splitter.validation.core.strategy.LengthLimitStrategy;
import com.novel.splitter.validation.core.strategy.DefaultDialogueStrategy;
import com.novel.splitter.validation.core.strategy.DefaultSegmentMergeStrategy;
import com.novel.splitter.validation.core.strategy.DefaultLengthLimitStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义段构建器
 * <p>
 * 负责将微小的物理段落 (RawParagraph) 合并为具有一定语义完整性的片段 (SemanticSegment)。
 * 核心策略：
 * 1. 对话合并 (DialogueMerge)：连续的对话不被打断。
 * 2. 叙述合并 (NarrationMerge)：连续的短叙述段落合并。
 * </p>
 */
public class SemanticSegmentBuilder {

    /** 强制切分阈值（避免单个 Segment 过长） */
    public static final int DEFAULT_MAX_SEGMENT_LENGTH = 800; 

    /** 语义段类型：对话 */
    public static final String TYPE_DIALOGUE = "DIALOGUE";
    
    /** 语义段类型：叙述 */
    public static final String TYPE_NARRATION = "NARRATION";

    private DialogueStrategy dialogueStrategy;
    private SegmentMergeStrategy segmentMergeStrategy;
    private LengthLimitStrategy lengthLimitStrategy;

    public SemanticSegmentBuilder() {
        this.dialogueStrategy = new DefaultDialogueStrategy();
        this.segmentMergeStrategy = new DefaultSegmentMergeStrategy();
        this.lengthLimitStrategy = new DefaultLengthLimitStrategy(DEFAULT_MAX_SEGMENT_LENGTH);
    }

    public void setDialogueStrategy(DialogueStrategy dialogueStrategy) {
        this.dialogueStrategy = dialogueStrategy;
    }

    public void setSegmentMergeStrategy(SegmentMergeStrategy segmentMergeStrategy) {
        this.segmentMergeStrategy = segmentMergeStrategy;
    }

    public void setLengthLimitStrategy(LengthLimitStrategy lengthLimitStrategy) {
        this.lengthLimitStrategy = lengthLimitStrategy;
    }

    /**
     * 构建语义段列表
     * 
     * @param paragraphs 原始段落列表
     * @return 语义段列表
     */
    public List<SemanticSegment> build(List<RawParagraph> paragraphs) {
        List<SemanticSegment> segments = new ArrayList<>();
        
        // 增加空值防护
        if (paragraphs == null || paragraphs.isEmpty()) {
            return segments;
        }

        SegmentState state = new SegmentState(paragraphs.size());

        for (RawParagraph p : paragraphs) {
            // 空值防护
            if (p == null || p.getContent() == null || p.isEmpty()) {
                continue;
            }

            int pLength = p.getContent().length(); // 缓存长度，优化性能
            String type = dialogueStrategy.detectType(p, state.getCurrentType());
            
            boolean shouldSplit = false;

            if (!state.isEmpty()) {
                // 预测长度，判断是否超限 (Task 4.1)
                boolean lengthLimitReached = lengthLimitStrategy.isExceeded(state.getCurrentLength(), pLength);
                
                // 判断是否应该合并 (Task 4.3 严格切分)
                boolean shouldMerge = segmentMergeStrategy.shouldMerge(state.getBuffer(), p, state.getCurrentType(), type);
                
                if (lengthLimitReached || !shouldMerge) {
                    shouldSplit = true;
                }
            }

            if (shouldSplit) {
                segments.add(state.toSegment());
                state.clear();
            }

            state.add(p, type, pLength);
        }

        // 提交剩余部分
        if (!state.isEmpty()) {
            segments.add(state.toSegment());
        }

        return segments;
    }

    protected SemanticSegment createSegment(List<RawParagraph> paragraphs, String type) {
        // 创建一个新的 List 副本，避免引用问题
        return SemanticSegment.builder()
                .paragraphs(new ArrayList<>(paragraphs))
                .type(type)
                .build();
    }
}
