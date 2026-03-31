package com.novel.splitter.core;

import java.util.regex.Pattern;

/**
 * 边界关键字词典
 * 集中管理用于识别文本边界、对话标志、说话人动作等的正则表达式和关键字。
 */
public class BoundaryKeywordDictionary {

    /**
     * 匹配包含引号的文本（通常表示对话）
     */
    public static final Pattern QUOTE_PATTERN = Pattern.compile("[\"“].*[\"”]");

    /**
     * 匹配常见的说话动词后缀（如：他说：、喊道：）
     */
    public static final Pattern SPEAKING_VERB_SUFFIX = Pattern.compile(".*(说|道|问|喊|叫|回复|表示)[:：]$");
    
    // 可以在这里扩展更多的关键字或模式
}
