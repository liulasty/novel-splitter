package com.novel.splitter.llm.client.impl;

import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekLlmClientTest {

    @Test
    void buildUserContent_includesPrefixContext() {
        ContextBlock block = ContextBlock.builder()
                .chunkId("c1").content("正文").prefixContext("上文")
                .build();
        Prompt prompt = Prompt.builder()
                .contextBlocks(List.of(block)).userQuestion("问题")
                .build();

        String content = DeepSeekLlmClient.buildUserContent(prompt);

        assertTrue(content.contains("[上文接续]\n上文\n[正文]\n正文"));
    }

    @Test
    void buildUserContent_withoutPrefixContext_plainContent() {
        ContextBlock block = ContextBlock.builder().chunkId("c1").content("正文").build();
        Prompt prompt = Prompt.builder()
                .contextBlocks(List.of(block)).userQuestion("q")
                .build();

        String content = DeepSeekLlmClient.buildUserContent(prompt);

        assertTrue(content.contains("Content: 正文"));
        assertFalse(content.contains("[上文接续]"));
    }
}
