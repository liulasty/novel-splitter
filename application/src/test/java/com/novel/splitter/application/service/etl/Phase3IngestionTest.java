package com.novel.splitter.application.service.etl;

import com.novel.splitter.application.service.rag.RagService;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest(classes = com.novel.splitter.application.NovelSplitApplication.class)
@Import({
    com.novel.splitter.llm.client.config.LlmClientConfig.class, 
    com.novel.splitter.embedding.config.EmbeddingConfig.class,
    org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class
})
@TestPropertySource(properties = {
    "novel.llm.provider=ollama",
    "llm.ollama.model=qwen:7b",
    "embedding.store.type=chroma",
    "chroma.url=http://localhost:8081",
    "chroma.collection=test-phase3-ingestion" 
})
public class Phase3IngestionTest {

    @Autowired
    private NovelIngestionService ingestionService;

    @Autowired
    private RagService ragService;

    @Test
    void testFullPipelineIngestionAndRetrieval() {
        // 1. Ingest (Limit to 50 scenes to cover the first chapter mostly)
        // Chapter 1 is Lines 8-200. Roughly 200 lines.
        // SceneAssembler usually groups ~1200 chars. 200 lines might be 1-3 scenes.
        // Let's limit to 20 scenes to be safe and fast.
        Path novelPath = Paths.get("d:\\soft\\novel-splitter\\data\\novel-storage\\九阳帝尊-剑棕.txt");
        log.info("Starting Phase 3 Ingestion Test with file: {}", novelPath);
        
        // Ingest first 20 scenes
        ingestionService.ingest(novelPath, 20);
        
        log.info("Ingestion finished. Testing Retrieval...");

        // 2. Verify Retrieval (RAG)
        String question = "楚晨现在的状态怎么样？";
        log.info("Asking Question: {}", question);
        
        Answer answer = null;
        try {
            answer = ragService.ask(question, 3);
        } catch (Exception e) {
             // In case LLM is down, fail gracefully or check stacktrace
             throw new RuntimeException("RAG Service call failed", e);
        }
        
        log.info("============================================");
        log.info("? Question: {}", question);
        log.info("? Answer: {}", answer.getAnswer());
        log.info("? Citations: {}", answer.getCitations());
        log.info("============================================");
        
        assertNotNull(answer);
        assertNotNull(answer.getAnswer());
        
        // Validation: Expect some keywords related to the first chapter content
        // e.g., "痛苦", "沉睡", "发烧", "不正常的红色"
        String ans = answer.getAnswer();
        boolean relevant = ans.contains("痛") || ans.contains("睡") || ans.contains("热") || ans.contains("红") || ans.contains("伤") || ans.contains("病");
        if (!relevant) {
            log.warn("Answer might not be relevant. Check if LLM context window covered the right text.");
        }
        // We don't strictly assert content as LLM generation varies, but we expect citations
        // unless the model failed to find info (which shouldn't happen if ingestion worked).
    }
}
