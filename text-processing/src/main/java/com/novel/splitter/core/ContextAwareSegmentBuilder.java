package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.validation.core.SemanticSegmentBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文感知语义段构建器 (Phase 2)
 * <p>
 * 增强特性：
 * 1. 识别 Anchor (标题、代码块) 并特殊处理。
 * 2. 增强的对话识别 (支持混合结构)。
 * 3. 动作描写吸附 (Adsorption)。
 * 4. 基于词项重叠度评估语义相似性。
 * 在 NLP 和 RAG 系统中，用于将段落组合成具有上下文连贯性的语义段落（Segment）。
 * </p>
 */
@Slf4j
public class ContextAwareSegmentBuilder extends SemanticSegmentBuilder {

    /**
     * 单个语义段落的最大长度限制，防止生成的片段过长。
     */
    private static final int MAX_SEGMENT_LENGTH = 800;
    
    private final SpeakerModel speakerModel;
    private final ParagraphRelevanceScorer relevanceScorer;

    /**
     * 默认构造函数。
     * <p>
     * 不使用向量嵌入服务，仅依赖基于规则的合并逻辑。
     * </p>
     */
    public ContextAwareSegmentBuilder() {
        this.speakerModel = new SpeakerModel();
        this.relevanceScorer = new ParagraphRelevanceScorer(speakerModel);
    }

    /**
     * 构建语义段落列表。
     * <p>
     * 遍历原始段落列表，根据段落类型、锚点属性以及语义相似度（或规则），
     * 将相关的段落合并为一个 {@link SemanticSegment}。
     * </p>
     *
     * @param paragraphs 原始段落列表
     * @return 构建好的语义段落列表
     */
    @Override
    public List<SemanticSegment> build(List<RawParagraph> paragraphs) {
        List<SemanticSegment> segments = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return segments;
        }

        // 用于暂存待合并的段落
        List<RawParagraph> buffer = new ArrayList<>();
        // 当前缓冲区中段落的类型
        String currentType = null;
        // 当前缓冲区中累计的文本长度
        int currentLength = 0;

        for (RawParagraph p : paragraphs) {
            // 跳过空段落
            if (p.isEmpty()) continue;

            String type = detectType(p);
            boolean isAnchor = p.isAnchor();
            
            // 标记当前段落是否应该触发切分（即不合并到缓冲区，而是开始新的语义段）
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
                    // 同类型时优先做语义判断；跨类型时交给规则评分（保留对话/旁白吸附能力）
                    if (currentType.equals(type)) {
                        Boolean semanticMerge = evaluateSemanticMerge(buffer, p);
                        if (semanticMerge != null && !semanticMerge) {
                            // 如果有明确的语义决定且建议拆分
                            shouldSplit = true;
                        }
                    } else {
                        boolean canMerge = canMerge(buffer, p, currentType, type);
                        if (!canMerge) {
                            shouldSplit = true;
                        }
                    }
                }
            }

            // 强制长度限制 (代码块/锚点除外)，防止单个 Segment 超出最大限制
            if (currentLength > MAX_SEGMENT_LENGTH && !isAnchor) {
                shouldSplit = true;
            }

            // 如果需要切分且缓冲区不为空，则将缓冲区内容生成为一个新的语义段落
            if (!buffer.isEmpty() && shouldSplit) {
                segments.add(createSegment(buffer, currentType));
                buffer.clear();
                currentLength = 0;
                // 更新当前类型为新段落的类型
                currentType = isAnchor ? p.getType().name() : type;
            } else if (buffer.isEmpty()) {
                 // 缓冲区为空时，直接初始化当前类型
                 currentType = isAnchor ? p.getType().name() : type;
            }

            // 将当前段落加入缓冲区，并更新累计长度
            buffer.add(p);
            currentLength += p.getContent().length();
        }

        // 处理遍历结束后缓冲区中剩余的段落
        if (!buffer.isEmpty()) {
            segments.add(createSegment(buffer, currentType));
        }

        return segments;
    }

    /**
     * 评估基于语义的合并决定。
     * <p>
     * 通过计算当前段落与缓冲区最后一个段落的词项重叠度，
     * 判断是否应该将它们合并。
     * </p>
     *
     * @param buffer 当前正在构建的段落缓冲区
     * @param current 当前需要判断的段落
     * @return 如果相似度高返回 true (合并)；相似度低返回 false (切分)；无法判断则返回 null
     */
    private Boolean evaluateSemanticMerge(List<RawParagraph> buffer, RawParagraph current) {
        if (buffer.isEmpty()) return null;
        RawParagraph last = buffer.get(buffer.size() - 1);

        try {
            String content1 = last.getContent();
            String content2 = current.getContent();
            
            if (content1 == null || content1.trim().isEmpty() || content2 == null || content2.trim().isEmpty()) {
                return null;
            }

            // 在纯算法模块中使用词项重叠度近似语义相似性，避免外部向量服务依赖。
            double similarity = tokenOverlapSimilarity(content1, content2);

            if (similarity > 0.85) {
                return true; // 相似度高，建议合并
            } else if (similarity < 0.65) {
                return false; // 相似度低，建议切分
            }
        } catch (Exception e) {
            log.warn("Error evaluating semantic merge, falling back to rule-based: {}", e.getMessage());
            // 发生异常时回退到基于规则的评估
            return null;
        }
        // 相似度在 [0.65, 0.85] 之间，无法明确判断，返回 null 以触发基于规则的评估
        return null;
    }

    /**
     * 使用基于规则的方法判断是否可以合并。
     *
     * @param buffer 当前段落缓冲区
     * @param current 当前段落
     * @param prevType 缓冲区前一个段落的类型
     * @param currType 当前段落的类型
     * @return 如果可以合并则返回 true，否则返回 false
     */
    private boolean canMerge(List<RawParagraph> buffer, RawParagraph current, String prevType, String currType) {
        if (buffer.isEmpty()) return false;
        RawParagraph last = buffer.get(buffer.size() - 1);
        return relevanceScorer.shouldMerge(last, current, prevType, currType);
    }

    private double tokenOverlapSimilarity(String content1, String content2) {
        Set<String> tokens1 = tokenize(content1);
        Set<String> tokens2 = tokenize(content2);
        if (tokens1.isEmpty() || tokens2.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);
        if (union.isEmpty()) {
            return 0;
        }
        return intersection.size() / (double) union.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase().split("[\\p{Punct}\\s]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 探测并返回段落的类型。
     * <p>
     * 优先判断是否为锚点，其次根据对话模型判断是否为对话，否则默认为旁白叙述。
     * </p>
     *
     * @param p 需要探测的原始段落
     * @return 段落的类型字符串表示
     */
    private String detectType(RawParagraph p) {
        if (p.isAnchor()) {
            return p.getType().name();
        }
        
        if (speakerModel.containsDialogue(p)) {
            return SemanticSegmentBuilder.TYPE_DIALOGUE;
        }
        return SemanticSegmentBuilder.TYPE_NARRATION;
    }
    
    /**
     * 将给定的段落列表构建为一个 {@link SemanticSegment} 实例。
     *
     * @param paragraphs 包含在语义段落中的原始段落列表
     * @param type 语义段落的类型
     * @return 构建好的语义段落对象
     */
    protected SemanticSegment createSegment(List<RawParagraph> paragraphs, String type) {
        return SemanticSegment.builder()
                .paragraphs(new ArrayList<>(paragraphs))
                .type(type)
                .build();
    }
}
