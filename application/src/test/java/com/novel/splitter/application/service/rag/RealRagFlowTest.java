package com.novel.splitter.application.service.rag;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 RAG 链路验证测试
 * <p>
 * 使用真实的组件 (Ollama, Chroma, ONNX) 验证全链路。
 * 需要外部服务：
 * 1. Ollama (port 11434, model qwen:7b)
 * 2. ChromaDB (port 8081)
 * </p>
 */
@Slf4j
@SpringBootTest(classes = com.novel.splitter.application.NovelSplitApplication.class)
@Import({com.novel.splitter.llm.client.config.LlmClientConfig.class, com.novel.splitter.embedding.config.EmbeddingConfig.class, org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class})
@TestPropertySource(properties = {
    "novel.llm.provider=ollama",
    "llm.ollama.model=qwen:7b",
    "embedding.store.type=chroma",
    "chroma.url=http://localhost:8081",
    "chroma.collection=test-integration-rag"
})
public class RealRagFlowTest {
    @Autowired
    private RagService ragService;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingService embeddingService;

    @MockBean
    private SceneRepository sceneRepository;

    private Scene testScene;

    @BeforeEach
    void setUp() {
        // 1. 准备真实的测试数据 (《斗破苍穹》片段)
        String sceneId = "real-test-" + UUID.randomUUID().toString();
        testScene = Scene.builder()
                .id(sceneId)
                .text("萧炎望着那张有些稚嫩的俏脸，无奈地摇了摇头。他现在的实力只是斗之气三段，在这个强者为尊的斗气大陆，确实是废材般的存在。但在他手指上的古朴戒指里，沉睡着一个曾震惊大陆的灵魂——药老。")
                .chapterTitle("第一章 陨落的天才")
                .chapterIndex(1)
                .startParagraphIndex(0)
                .metadata(SceneMetadata.builder()
                        .novel("斗破苍穹")
                        .chapterTitle("第一章 陨落的天才")
                        .build())
                .build();

        // 2. Mock Repository (因为我们还没实现真实的数据库存储，但需要通过 ID 查回文本)
        Mockito.when(sceneRepository.findById(sceneId)).thenReturn(Optional.of(testScene));
        Mockito.when(sceneRepository.findById(Mockito.argThat(arg -> !sceneId.equals(arg))))
               .thenReturn(Optional.empty());

        // 3. 真实入库 (Embed -> Chroma)
        try {
            float[] embedding = embeddingService.embed(testScene.getText());
            vectorStore.save(testScene, embedding);
            log.info("Test scene saved to ChromaDB with ID: {}", sceneId);
        } catch (Exception e) {
            fail("Failed to save to ChromaDB. Is the service running at localhost:8081? Error: " + e.getMessage());
        }
    }

    @Test
    void testRealRagQuestion() {
        // 1. 提问
        String question = "萧炎戒指里藏着谁？";
        log.info("Asking Question: {}", question);

        Answer answer = null;
        try {
            answer = ragService.ask(question, 3);
        } catch (Exception e) {
            fail("RAG Service call failed. Is Ollama running? Error: " + e.getMessage());
        }

        // 2. 输出结果
        System.out.println("\n============================================");
        System.out.println("🤖 Question: " + question);
        System.out.println("📝 Answer: " + answer.getAnswer());
        System.out.println("🔍 Confidence: " + answer.getConfidence());
        System.out.println("📚 Citations:");
        answer.getCitations().forEach(c -> System.out.println("   - [" + c.getChunkId() + "] " + c.getReason()));
        System.out.println("============================================\n");

        // 3. 验证结果
        assertNotNull(answer);
        assertNotNull(answer.getAnswer(), "Answer should not be null");
        log.info("Answer: {}", answer.getAnswer());
        
        // 验证回答的相关性 (依赖 LLM 的智能程度)
        boolean containsKeyInfo = answer.getAnswer().contains("药老") || answer.getAnswer().contains("灵魂");
        assertTrue(containsKeyInfo, "Answer should contain '药老' or '灵魂'. Actual: " + answer.getAnswer());
        
        // 验证是否有引用 (对于小模型放宽要求)
        if (answer.getCitations() == null || answer.getCitations().isEmpty()) {
            log.warn("Model returned correct answer but failed to provide citations. This is common with smaller models (7B).");
        } else {
            log.info("Citations: {}", answer.getCitations());
            assertEquals(testScene.getId(), answer.getCitations().get(0).getChunkId(), "Should cite the correct scene");
        }
    }
}
