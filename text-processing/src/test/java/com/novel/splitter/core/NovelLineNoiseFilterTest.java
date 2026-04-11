package com.novel.splitter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NovelLineNoiseFilterTest {

    @Test
    void skipsTocHeaderAndSeparatorRules() {
        assertTrue(NovelLineNoiseFilter.shouldSkipParagraphLine("章节目录"));
        assertTrue(NovelLineNoiseFilter.shouldSkipParagraphLine("------------"));
        assertTrue(NovelLineNoiseFilter.shouldSkipParagraphLine("——————"));
    }

    @Test
    void keepsNormalLines() {
        assertFalse(NovelLineNoiseFilter.shouldSkipParagraphLine(""));
        assertFalse(NovelLineNoiseFilter.shouldSkipParagraphLine("第一章 仙山下的失足少年"));
        assertFalse(NovelLineNoiseFilter.shouldSkipParagraphLine("--"));
    }
}
