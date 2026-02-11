package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 上下文感知语义段构建器 (Phase 2)
 * <p>
 * 增强特性：
 * 1. 识别 Anchor (标题、代码块) 并特殊处理。
 * 2. 增强的对话识别 (支持混合结构)。
 * 3. 动作描写吸附 (Adsorption)。
 * </p>
 */
public class ContextAwareSegmentBuilder extends SemanticSegmentBuilder {

    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"“].*[\"”]");
    // 简单的说话动词后缀匹配 (Heuristic)
    private static final Pattern SPEAKING_VERB_SUFFIX = Pattern.compile(".*(说|道|问|喊|叫|回复|表示)[:：]$");
    
    private static final int MAX_SEGMENT_LENGTH = 800;

    @Override
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
            boolean isAnchor = p.isAnchor();
            
            boolean shouldSplit = false;

            if (isAnchor) {
                // Anchor 总是倾向于独立，或者仅与同类 Anchor 合并 (如连续代码行)
                if (!buffer.isEmpty()) {
                    RawParagraph last = buffer.get(buffer.size() - 1);
                    // 如果之前 buffer 不是同类型的 Anchor，或者虽是同类型但应该是独立的(如Header)，则切分
                    // Header 总是独立
                    if (p.getType().name().equals("HEADER") || last.getType() != p.getType()) {
                        shouldSplit = true;
                    }
                }
                type = p.getType().name(); 
            } else {
                // 普通文本逻辑
                if (currentType != null && !currentType.equals(type)) {
                    // 尝试吸附
                    boolean canMerge = canMerge(buffer, p, currentType, type);
                    if (!canMerge) {
                        shouldSplit = true;
                    }
                }
            }

            // 强制长度限制 (代码块/锚点除外)
            if (currentLength > MAX_SEGMENT_LENGTH && !isAnchor) {
                shouldSplit = true;
            }

            if (!buffer.isEmpty() && shouldSplit) {
                segments.add(createSegment(buffer, currentType));
                buffer.clear();
                currentLength = 0;
                currentType = isAnchor ? p.getType().name() : type;
            } else if (buffer.isEmpty()) {
                 currentType = isAnchor ? p.getType().name() : type;
            }

            buffer.add(p);
            currentLength += p.getContent().length();
        }

        if (!buffer.isEmpty()) {
            segments.add(createSegment(buffer, currentType));
        }

        return segments;
    }

    private boolean canMerge(List<RawParagraph> buffer, RawParagraph current, String prevType, String currType) {
        // 1. 只有 TEXT 类型参与吸附 (Anchor 不参与)
        if (current.isAnchor()) return false;
        if (!buffer.isEmpty() && buffer.get(buffer.size()-1).isAnchor()) return false;
        
        // 2. Narration (短) + Dialogue -> 合并 (例如：他说： "...")
        if (SemanticSegmentBuilder.TYPE_NARRATION.equals(prevType) && SemanticSegmentBuilder.TYPE_DIALOGUE.equals(currType)) {
            RawParagraph last = buffer.get(buffer.size() - 1);
            // 前一段少于 50 字，可能是前缀
            if (last.getContent().length() < 50) {
                return true;
            }
        }
        
        // 3. Dialogue + Narration (短) -> 合并 (例如： "..." 他笑了笑。)
        if (SemanticSegmentBuilder.TYPE_DIALOGUE.equals(prevType) && SemanticSegmentBuilder.TYPE_NARRATION.equals(currType)) {
            // 当前段少于 50 字，可能是后缀动作
            if (current.getContent().length() < 50) {
                return true;
            }
        }

        return false;
    }

    private String detectType(RawParagraph p) {
        if (p.isAnchor()) {
            return p.getType().name();
        }
        
        String content = p.getContent();
        if (QUOTE_PATTERN.matcher(content).find()) {
            return SemanticSegmentBuilder.TYPE_DIALOGUE;
        }
        return SemanticSegmentBuilder.TYPE_NARRATION;
    }
    
    private SemanticSegment createSegment(List<RawParagraph> paragraphs, String type) {
        return SemanticSegment.builder()
                .paragraphs(new ArrayList<>(paragraphs))
                .type(type)
                .build();
    }
}
