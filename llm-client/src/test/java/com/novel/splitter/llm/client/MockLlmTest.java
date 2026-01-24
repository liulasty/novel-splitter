package com.novel.splitter.llm.client;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.impl.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MockLlmTest {

    @Test
    void testMockLlmBehavior() {
        // 1. 准备 Mock 数据
        SceneMetadata metadata = SceneMetadata.builder()
                .novel("Harry Potter")
                .chapterTitle("The Boy Who Lived")
                .build();

        ContextBlock block1 = ContextBlock.builder()
                .chunkId("chunk-001")
                .content("Mr. Dursley was the director of a firm called Grunnings, which made drills.")
                .sceneMetadata(metadata)
                .build();

        ContextBlock block2 = ContextBlock.builder()
                .chunkId("chunk-002")
                .content("Mrs. Dursley was thin and blonde and had nearly twice the usual amount of neck.")
                .sceneMetadata(metadata)
                .build();

        Prompt prompt = Prompt.builder()
                .systemInstruction("You are a helpful assistant.")
                .userQuestion("Who are the Dursleys?")
                .contextBlocks(Arrays.asList(block1, block2))
                .build();

        // 2. 调用 Mock LLM
        LlmClient llmClient = new MockLlmClient();
        Answer answer = llmClient.chat(prompt);

        // 3. 打印结果 (便于人工验证 "像人类回答")
        System.out.println("=== Mock LLM Response ===");
        System.out.println("Answer: " + answer.getAnswer());
        System.out.println("Confidence: " + answer.getConfidence());
        System.out.println("Citations:");
        answer.getCitations().forEach(c -> System.out.println(" - [" + c.getChunkId() + "] " + c.getReason()));
        System.out.println("=========================");

        // 4. 验证约束
        assertNotNull(answer.getAnswer());
        assertFalse(answer.getAnswer().isEmpty());
        
        assertNotNull(answer.getCitations());
        assertEquals(2, answer.getCitations().size()); // 应该引用传入的两个块
        
        assertEquals("chunk-001", answer.getCitations().get(0).getChunkId());
        
        assertTrue(answer.getConfidence() >= 0.6 && answer.getConfidence() <= 0.95);
        
        // 验证提取的名词是否出现在回答中
        assertTrue(answer.getAnswer().contains("Mr") || answer.getAnswer().contains("Dursley"));
    }
}
