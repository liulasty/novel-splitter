package com.novel.splitter.core;

import java.util.regex.Pattern;

/**
 * 边界关键字词典
 * <p>
 * 集中管理用于识别文本边界、对话标志、说话人动作等的正则表达式和关键字。
 * 该类作为常量字典使用，提供自然语言处理和 RAG（检索增强生成）系统所需的正则模式。
 * </p>
 */
public class BoundaryKeywordDictionary {

    /**
     * 匹配包含引号的文本（通常表示对话）。
     * <p>
     * 支持中英文的引号匹配，用于提取小说或文本中的人物对话内容。
     * </p>
     */
    public static final Pattern QUOTE_PATTERN = Pattern.compile("[\"“].*[\"”]");

    /**
     * 匹配常见的说话动词后缀（如：他说：、喊道：）。
     * <p>
     * 用于识别引出对话的提示语，支持多种常见的中文说话动词及中英文冒号。
     * </p>
     */
    public static final Pattern SPEAKING_VERB_SUFFIX = Pattern.compile(".*(说|道|问|喊|叫|回复|表示)[:：]$");
    
    // 可以在这里扩展更多的关键字或模式，例如心理活动的标志等
}
