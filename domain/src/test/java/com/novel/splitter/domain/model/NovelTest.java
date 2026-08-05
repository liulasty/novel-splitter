package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.NovelStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void checkCanReadScenes_blocksWhileScenesBeingWritten() {
        assertThrows(IllegalStateException.class,
                Novel.builder().id("n1").status(NovelStatus.PENDING).build()::checkCanReadScenes);
        // 场景在 SPLITTING 阶段写入，期间读取可能不一致，应被拦截。
        assertThrows(IllegalStateException.class,
                Novel.builder().id("n2").status(NovelStatus.SPLITTING).build()::checkCanReadScenes);

        assertDoesNotThrow(Novel.builder().id("n3").status(NovelStatus.PARSED).build()::checkCanReadScenes);
        assertDoesNotThrow(Novel.builder().id("n4").status(NovelStatus.SPLIT_COMPLETED).build()::checkCanReadScenes);
    }
}
