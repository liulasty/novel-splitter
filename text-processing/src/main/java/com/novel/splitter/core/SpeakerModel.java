package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;

import java.util.regex.Matcher;

/**
 * 说话人模型分析器
 * 核心模型，用于在NLP/RAG处理中判断文本段落是否包含对话，或者是否属于说话人的动作描述。
 * 这些特征能够有效帮助小说文本切割系统确定语义边界和场景边界。
 */
public class SpeakerModel {

    /**
     * 判断给定的原始段落是否包含对话内容。
     * 该方法通过匹配预定义的对话引号模式来识别对话。
     *
     * @param paragraph 原始段落对象
     * @return 如果段落内容中存在匹配引号模式的对话则返回 true，否则返回 false。
     */
    public boolean containsDialogue(RawParagraph paragraph) {
        // 如果传入的段落为空或其内容为空，则认为不包含对话
        if (paragraph == null || paragraph.getContent() == null) {
            return false;
        }
        // 使用 BoundaryKeywordDictionary 中的 QUOTE_PATTERN 正则表达式来匹配对话引号
        return BoundaryKeywordDictionary.QUOTE_PATTERN.matcher(paragraph.getContent()).find();
    }

    /**
     * 判断给定的原始段落是否为潜在的说话人动作描述（作为对话前缀或后缀）。
     * 此类段落通常是较短的叙述段落，往往包含特定的表示说话动作的动词，或者是简短的上下文动作。
     *
     * @param paragraph 原始段落对象
     * @return 如果段落是说话人的动作描述则返回 true，否则返回 false。
     */
    public boolean isSpeakerAction(RawParagraph paragraph) {
        // 如果传入的段落为空或其内容为空，则认为不是说话人动作
        if (paragraph == null || paragraph.getContent() == null) {
            return false;
        }
        String content = paragraph.getContent();
        
        // 使用正则检查段落内容是否包含明显表示说话动作的后缀动词
        Matcher matcher = BoundaryKeywordDictionary.SPEAKING_VERB_SUFFIX.matcher(content);
        if (matcher.find()) {
            return true;
        }
        
        // 或者判断段落是否是一个长度小于 50 个字符的极短叙述段落，这类段落通常被视作上下文相关的说话动作
        return content.length() < 50;
    }
}
