package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;

import java.util.regex.Matcher;

/**
 * 说话人模型
 * 用于判断段落是否包含对话，或者是否是说话人的动作描述。
 */
public class SpeakerModel {

    /**
     * 判断段落是否包含对话
     */
    public boolean containsDialogue(RawParagraph paragraph) {
        if (paragraph == null || paragraph.getContent() == null) {
            return false;
        }
        return BoundaryKeywordDictionary.QUOTE_PATTERN.matcher(paragraph.getContent()).find();
    }

    /**
     * 判断段落是否是潜在的说话人动作（前缀或后缀）
     * 通常是较短的叙述段落，可能包含特定的说话动词。
     */
    public boolean isSpeakerAction(RawParagraph paragraph) {
        if (paragraph == null || paragraph.getContent() == null) {
            return false;
        }
        String content = paragraph.getContent();
        
        // 如果包含明显的说话动词后缀
        Matcher matcher = BoundaryKeywordDictionary.SPEAKING_VERB_SUFFIX.matcher(content);
        if (matcher.find()) {
            return true;
        }
        
        // 或者是一个很短的叙述段落（小于 50 字），可能被视为上下文动作
        return content.length() < 50;
    }
}
