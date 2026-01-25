package com.novel.splitter.assembler.support;

/**
 * Token 计数器接口
 */
public interface TokenCounter {
    
    /**
     * 计算文本的 Token 数量
     * @param text 输入文本
     * @return Token 数量
     */
    int count(String text);
}
