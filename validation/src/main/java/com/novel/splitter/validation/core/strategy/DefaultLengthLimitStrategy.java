package com.novel.splitter.validation.core.strategy;

/**
 * 默认长度限制策略
 */
public class DefaultLengthLimitStrategy implements LengthLimitStrategy {

    private final int maxLength;

    public DefaultLengthLimitStrategy(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public boolean isExceeded(int currentLength, int nextLength) {
        // 在将段落加入 buffer 之前预测长度（Task 4.1）
        return (currentLength + nextLength) > maxLength;
    }
}
