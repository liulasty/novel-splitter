package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"“].*[\"”]");
    // 强制切分阈值（避免单个 Segment 过长）
    private static final int MAX_SEGMENT_LENGTH = 800; 

    public static final String TYPE_DIALOGUE = "DIALOGUE";
    public static final String TYPE_NARRATION = "NARRATION";

    /**
     * 构建语义段列表
     */
    public List<SemanticSegment> build(List<RawParagraph> paragraphs) {
        List<SemanticSegment> segments = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return segments;
        }

        List<RawParagraph> buffer = new ArrayList<>();
        String currentType = null;
        int currentLength = 0;

        for (RawParagraph p : paragraphs) {
            if (p.isEmpty()) continue;

            String type = detectType(p);
            
            // 状态切换或长度超限时，提交当前 buffer
            boolean typeChanged = currentType != null && !currentType.equals(type);
            boolean lengthLimitReached = currentLength > MAX_SEGMENT_LENGTH;

            if (!buffer.isEmpty() && (typeChanged || lengthLimitReached)) {
                segments.add(createSegment(buffer, currentType));
                buffer.clear();
                currentLength = 0;
            }

            // 更新状态
            if (buffer.isEmpty()) {
                currentType = type;
            }
            
            buffer.add(p);
            currentLength += p.getContent().length();
        }

        // 提交剩余部分
        if (!buffer.isEmpty()) {
            segments.add(createSegment(buffer, currentType));
        }

        return segments;
    }

    private SemanticSegment createSegment(List<RawParagraph> paragraphs, String type) {
        // 创建一个新的 List 副本，避免引用问题
        return SemanticSegment.builder()
                .paragraphs(new ArrayList<>(paragraphs))
                .type(type)
                .build();
    }

    /**
     * 探测段落类型
     * 简单规则：包含引号视为对话，否则为叙述
     */
    private String detectType(RawParagraph p) {
        String content = p.getContent();
        // 1. 显式引号
        if (QUOTE_PATTERN.matcher(content).find()) {
            return TYPE_DIALOGUE;
        }
        // 2. 只有标点和极短文字，通常是语气词，跟随上下文（这里暂归为 Narration，依靠后续逻辑优化）
        // TODO: 更复杂的上下文感知逻辑
        
        return TYPE_NARRATION;
    }
}
