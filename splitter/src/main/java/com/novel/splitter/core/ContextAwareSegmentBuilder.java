package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.embedding.api.EmbeddingService;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文感知语义段构建器 (Phase 2)
 * <p>
 * 增强特性：
 * 1. 识别 Anchor (标题、代码块) 并特殊处理。
 * 2. 增强的对话识别 (支持混合结构)。
 * 3. 动作描写吸附 (Adsorption)。
 * 4. 集成 OnnxEmbeddingService 计算余弦相似度。
 * </p>
 */
public class ContextAwareSegmentBuilder extends SemanticSegmentBuilder {

    private static final int MAX_SEGMENT_LENGTH = 800;
    
    private final SpeakerModel speakerModel;
    private final ParagraphRelevanceScorer relevanceScorer;
    private final EmbeddingService embeddingService;

    public ContextAwareSegmentBuilder() {
        this(null);
    }

    public ContextAwareSegmentBuilder(EmbeddingService embeddingService) {
        this.speakerModel = new SpeakerModel();
        this.relevanceScorer = new ParagraphRelevanceScorer(speakerModel);
        this.embeddingService = embeddingService;
    }

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
                if (currentType != null) {
                    Boolean semanticMerge = evaluateSemanticMerge(buffer, p);
                    
                    if (semanticMerge != null) {
                        // 如果有明确的语义决定 (>0.85 合并, <0.65 拆分)
                        if (!semanticMerge) {
                            shouldSplit = true;
                        }
                    } else {
                        // 回退到基于类型的相关性评分
                        if (!currentType.equals(type)) {
                            boolean canMerge = canMerge(buffer, p, currentType, type);
                            if (!canMerge) {
                                shouldSplit = true;
                            }
                        }
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

    private Boolean evaluateSemanticMerge(List<RawParagraph> buffer, RawParagraph current) {
        if (buffer.isEmpty() || embeddingService == null) return null;
        RawParagraph last = buffer.get(buffer.size() - 1);

        try {
            float[] v1 = embeddingService.embed(last.getContent());
            float[] v2 = embeddingService.embed(current.getContent());
            double similarity = cosineSimilarity(v1, v2);

            if (similarity > 0.85) {
                return true; // merge
            } else if (similarity < 0.65) {
                return false; // cut
            }
        } catch (Exception e) {
            // Ignore embedding errors and fallback
        }
        return null;
    }

    private boolean canMerge(List<RawParagraph> buffer, RawParagraph current, String prevType, String currType) {
        if (buffer.isEmpty()) return false;
        RawParagraph last = buffer.get(buffer.size() - 1);
        return relevanceScorer.shouldMerge(last, current, prevType, currType);
    }

    private double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) return 0;
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String detectType(RawParagraph p) {
        if (p.isAnchor()) {
            return p.getType().name();
        }
        
        if (speakerModel.containsDialogue(p)) {
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
