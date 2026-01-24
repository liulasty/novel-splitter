package com.novel.splitter.domain.model;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/**
 * 语义段
 * <p>
 * 切分过程中的中间产物。
 * 例如：连续的一段对话、一段环境描写，可以被合并为一个 SemanticSegment。
 * </p>
 */
@Getter
@Builder
public class SemanticSegment {
    /**
     * 包含的原始段落
     */
    private final List<RawParagraph> paragraphs;

    /**
     * 类型 (如 DIALOGUE, NARRATION)
     */
    private final String type;
}
