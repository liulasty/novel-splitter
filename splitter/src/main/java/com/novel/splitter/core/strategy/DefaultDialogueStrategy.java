package com.novel.splitter.core.strategy;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.core.SemanticSegmentBuilder;
import java.util.regex.Pattern;

/**
 * 默认对话识别策略
 */
public class DefaultDialogueStrategy implements DialogueStrategy {

    // 匹配常规引号
    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\"“].*[\"”]");
    // 匹配冒号+引号组合（如：说道：“...”，支持段首/段尾引号判断）
    private static final Pattern COLON_QUOTE_PATTERN = Pattern.compile("[:：]\\s*[\"“].*[\"”]");
    // 匹配只有标点和极短文字的段落（可能是语气词，如“啊。”、“嗯？”）
    private static final Pattern SHORT_PARTICLE_PATTERN = Pattern.compile("^[\\p{P}\\p{S}\\s]*[\\u4e00-\\u9fa5]{0,3}[\\p{P}\\p{S}\\s]*$");
    // 匹配常见的对话标签（如：XX道，XX说）
    private static final Pattern DIALOGUE_TAG_PATTERN = Pattern.compile(".*(说|道|问|喊|叫|回复|表示)[:：]?$");

    @Override
    public String detectType(RawParagraph paragraph, String previousType) {
        if (paragraph == null || paragraph.getContent() == null) {
            return SemanticSegmentBuilder.TYPE_NARRATION;
        }

        String content = paragraph.getContent().trim();
        
        // 1. 如果包含冒号+引号，或者直接包含引号，认为是对话
        if (COLON_QUOTE_PATTERN.matcher(content).find() || QUOTE_PATTERN.matcher(content).find()) {
            return SemanticSegmentBuilder.TYPE_DIALOGUE;
        }

        // 2. 如果是只有标点和极短文字（语气词），且不含引号，跟随上下文类型（上下文感知）
        if (SHORT_PARTICLE_PATTERN.matcher(content).matches()) {
            if (previousType != null) {
                return previousType;
            }
        }

        // 3. 对话标签（“XX道”、“XX说”）自动归类为对话（或者跟随对话段落合并）
        // 如果我们将其识别为 DIALOGUE，它就会与相邻的 DIALOGUE 合并。
        if (DIALOGUE_TAG_PATTERN.matcher(content).matches()) {
            return SemanticSegmentBuilder.TYPE_DIALOGUE;
        }

        return SemanticSegmentBuilder.TYPE_NARRATION;
    }
}
