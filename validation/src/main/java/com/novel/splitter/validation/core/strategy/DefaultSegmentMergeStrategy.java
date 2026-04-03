package com.novel.splitter.validation.core.strategy;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.validation.core.SemanticSegmentBuilder;
import java.util.List;

/**
 * 默认段落合并策略
 */
public class DefaultSegmentMergeStrategy implements SegmentMergeStrategy {

    @Override
    public boolean shouldMerge(List<RawParagraph> buffer, RawParagraph current, String currentType, String nextType) {
        if (buffer == null || buffer.isEmpty()) {
            return true;
        }

        // 只有类型相同时才合并，确保“对话+叙述+对话”严格切分
        return currentType != null && currentType.equals(nextType);
    }
}
