package com.novel.splitter.core.strategy;

/**
 * 长度限制策略
 */
public interface LengthLimitStrategy {
    /**
     * 判断合并当前段落后是否超出长度限制
     *
     * @param currentLength 缓冲区当前总长度
     * @param nextLength 准备合并的段落长度
     * @return true 表示超出限制，需要截断；false 表示未超出
     */
    boolean isExceeded(int currentLength, int nextLength);
}
