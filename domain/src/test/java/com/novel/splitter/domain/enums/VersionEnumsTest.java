package com.novel.splitter.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 版本化切分相关枚举的契约测试。
 */
class VersionEnumsTest {

    @Test
    void splitStrategyContainsExpectedValues() {
        assertNotNull(SplitStrategy.valueOf("SCENE_BOUNDARY"));
        assertNotNull(SplitStrategy.valueOf("OVERLAP_CHUNK"));
        assertNotNull(SplitStrategy.valueOf("SEMANTIC"));
        assertEquals(3, SplitStrategy.values().length);
    }

    @Test
    void versionStatusContainsExpectedValues() {
        assertNotNull(VersionStatus.valueOf("PENDING"));
        assertNotNull(VersionStatus.valueOf("SPLITTING"));
        assertNotNull(VersionStatus.valueOf("SPLIT_DONE"));
        assertNotNull(VersionStatus.valueOf("EMBEDDING"));
        assertNotNull(VersionStatus.valueOf("EMBED_DONE"));
        assertNotNull(VersionStatus.valueOf("ACTIVE"));
        assertNotNull(VersionStatus.valueOf("FAILED"));
        assertNotNull(VersionStatus.valueOf("ABANDONED"));
        assertEquals(8, VersionStatus.values().length);
    }
}
