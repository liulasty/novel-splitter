package com.novel.splitter.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Novel 聚合激活指针 activeVersionTag 契约测试。
 */
class NovelTest {

    @Test
    void activeVersionTagIsMutableAndDefaultsNull() {
        Novel n = Novel.builder().id("n1").title("t").build();
        assertNull(n.getActiveVersionTag());
        n.setActiveVersionTag("v2");
        assertEquals("v2", n.getActiveVersionTag());
    }
}
