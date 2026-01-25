package com.novel.splitter.assembler.support;

import org.springframework.stereotype.Component;

/**
 * 简单 Token 计数器实现
 * <p>
 * 假设 1 个汉字 ≈ 1.5 tokens (简单估算)
 * </p>
 */
@Component
public class SimpleTokenCounter implements TokenCounter {

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 简单策略：字符数 * 1.5
        return (int) Math.ceil(text.length() * 1.5);
    }
}
