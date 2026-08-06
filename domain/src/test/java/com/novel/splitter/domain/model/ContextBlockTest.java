package com.novel.splitter.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextBlockTest {

    @Test
    void effectiveContent_withoutPrefix_returnsContent() {
        ContextBlock block = ContextBlock.builder().content("正文").build();
        assertEquals("正文", block.effectiveContent());
    }

    @Test
    void effectiveContent_withPrefix_prependsContinuation() {
        ContextBlock block = ContextBlock.builder().content("正文").prefixContext("上文").build();
        assertEquals("[上文接续]\n上文\n[正文]\n正文", block.effectiveContent());
    }

    @Test
    void effectiveContent_withBlankPrefix_returnsContent() {
        ContextBlock block = ContextBlock.builder().content("正文").prefixContext("  ").build();
        assertEquals("正文", block.effectiveContent());
    }
}
