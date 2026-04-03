package com.novel.splitter.validation.core.strategy;

import com.novel.splitter.domain.model.RawParagraph;
import java.util.List;

/**
 * 段落合并策略
 */
public interface SegmentMergeStrategy {
    /**
     * 判断当前段落是否应该与前置缓冲区合并
     *
     * @param buffer 当前段落缓冲区
     * @param current 当前准备处理的段落
     * @param currentType 缓冲区的语义类型
     * @param nextType 当前段落的语义类型
     * @return true 表示合并，false 表示独立新段
     */
    boolean shouldMerge(List<RawParagraph> buffer, RawParagraph current, String currentType, String nextType);
}
