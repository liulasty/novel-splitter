package com.novel.splitter.application.service.rag;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.embedding.mock.MockVectorStore;
import com.novel.splitter.embedding.store.InMemoryVectorStore;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.retrieval.api.RetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 系统全面自测套件
 * <p>
 * 包含三个层级的验证：
 * 1. 结构完整性（Structural Integrity）
 * 2. 行为一致性（Behavioral Consistency）
 * 3. 反事实验证（Counterfactual Verification）
 * </p>
 */
@SpringBootTest
@DisplayName("RAG 系统全面自测")
// @Import(RagVerificationTest.TestConfig.class) // Removed TestConfig to avoid bean duplication
public class RagVerificationTest {

    @Autowired
    private RagService ragService;

    @Autowired
    private VectorStore vectorStore; // Inject interface, cast to Mock if needed

    @MockBean
    private SceneRepository sceneRepository;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private RetrievalService retrievalService;

    @Autowired
    private ContextAssembler contextAssembler;

    @BeforeEach
    void setUp() {
        clearVectorStore();
    }

    private void clearVectorStore() {
        if (vectorStore instanceof MockVectorStore) {
            ((MockVectorStore) vectorStore).clear();
        } else if (vectorStore instanceof InMemoryVectorStore) {
            ((InMemoryVectorStore) vectorStore).reset();
        }
    }

    @Nested
    @DisplayName("Level 1: 结构完整性验证")
    class StructuralIntegrityTest {

        @Test
        @DisplayName("输入无关问题，应返回合法结构")
        void testIrrelevantQuestion() {
            Answer answer = ragService.ask("这本小说里有宇宙飞船吗？", 3);
            assertValidStructure(answer);
        }

        @Test
        @DisplayName("输入极短问题，应返回合法结构")
        void testShortQuestion() {
            Answer answer = ragService.ask("谁？", 3);
            assertValidStructure(answer);
        }

        @Test
        @DisplayName("输入超长问题，应返回合法结构")
        void testLongQuestion() {
            String longQuestion = "这个问题非常非常长".repeat(20);
            Answer answer = ragService.ask(longQuestion, 3);
            assertValidStructure(answer);
        }

        private void assertValidStructure(Answer answer) {
            assertNotNull(answer, "Answer 对象不能为 null");
            assertNotNull(answer.getAnswer(), "answer 字段不能为 null");
            assertNotNull(answer.getCitations(), "citations 字段不能为 null (应为 List)");
            assertNotNull(answer.getConfidence(), "confidence 字段不能为 null");
            assertTrue(answer.getConfidence() >= 0.0 && answer.getConfidence() <= 1.0, 
                    "confidence 必须在 [0, 1] 区间内");
        }
    }

    @Nested
    @DisplayName("Level 2: 行为一致性验证")
    class BehavioralConsistencyTest {

        @Test
        @DisplayName("恶意测试1：零证据时应拒绝回答")
        void testZeroEvidence() {
            // 不往 vectorStore 存任何东西 -> 检索结果为空
            Answer answer = ragService.ask("非常具体的问题", 3);

            // 验证
            assertTrue(answer.getCitations().isEmpty(), "零证据时 citations 必须为空");
            assertEquals(0.0, answer.getConfidence(), "零证据时 confidence 应为 0");
            assertTrue(answer.getAnswer().contains("cannot find") || answer.getAnswer().contains("relevant"),
                    "回答应包含拒绝模板 (cannot find / relevant)");
        }

        @Test
        @DisplayName("恶意测试2：单证据时仅引用该证据")
        void testSingleEvidence() {
            // 准备单条数据
            Scene scene = createScene("chunk-001", "Harry Potter is a wizard.");
            registerScene(scene);

            Answer answer = ragService.ask("Who is Harry?", 3);

            assertEquals(1, answer.getCitations().size(), "应仅引用 1 条证据");
            assertEquals("chunk-001", answer.getCitations().get(0).getChunkId());
        }

        @Test
        @DisplayName("恶意测试3：证据冲突测试")
        void testConflictingEvidence() {
            // 准备冲突数据
            Scene sceneA = createScene("chunk-A", "Chu Chen is an outer disciple.");
            Scene sceneB = createScene("chunk-B", "Chu Chen is an inner disciple.");
            
            registerScene(sceneA);
            registerScene(sceneB);

            Answer answer = ragService.ask("What is Chu Chen's status?", 3);

            // 验证 Mock LLM 是否读取了两者
            // 由于 MockLlmClient 逻辑是简单的模板拼接，它应该会引用所有传入的块
            assertTrue(answer.getCitations().stream().anyMatch(c -> c.getChunkId().equals("chunk-A")), "应引用 Chunk A");
            assertTrue(answer.getCitations().stream().anyMatch(c -> c.getChunkId().equals("chunk-B")), "应引用 Chunk B");
            
            // 验证回答是否包含关键信息 (Mock 行为)
            assertTrue(answer.getAnswer().contains("Chu") || answer.getAnswer().contains("Chen"), "回答应包含关键词");
        }

        @Test
        @DisplayName("恶意测试4：引用完整性测试 (不存在的 ChunkId)")
        void testCitationIntegrity() {
            // 1. 准备数据
            Scene scene = createScene("chunk-valid", "Valid content.");
            registerScene(scene);

            // 2. 构造恶意的 LlmClient
            LlmClient maliciousLlm = Mockito.mock(LlmClient.class);
            Mockito.when(maliciousLlm.chat(Mockito.any())).thenReturn(
                    Answer.builder()
                            .answer("I am hallucinating.")
                            .citations(List.of(
                                    new Answer.Citation("chunk-valid", "Reason 1"),
                                    new Answer.Citation("chunk-fake-999", "Reason 2") // 不存在的 ID
                            ))
                            .confidence(0.9)
                            .build()
            );

            // 3. 手动组装 RagService (注入恶意 LLM)
            RagService testRagService = new RagService(retrievalService, contextAssembler, maliciousLlm);

            // 4. 执行并验证
            // 期望抛出异常，或者返回 Answer 但被标记为 invalid
            assertThrows(IllegalStateException.class, () -> {
                testRagService.ask("Any question", 1);
            }, "当 LLM 引用了不存在的 chunkId 时，系统应抛出 IllegalStateException");
        }
    }

    @Nested
    @DisplayName("Level 3: 反事实验证")
    class CounterfactualTest {

        @Test
        @DisplayName("修改原文，回答应随之改变")
        void testCounterfactual() {
            String question = "Why was Chu Chen injured?";
            String sceneId = "chunk-injury";
            
            // 1. 原始版本
            Scene originalScene = createScene(sceneId, "Chu Chen was injured because he fell while Picking Herbs.");
            registerScene(originalScene);
            
            Answer answer1 = ragService.ask(question, 3);
            String content1 = answer1.getAnswer();
            
            // 清理环境，准备第二次
            clearVectorStore();
            
            // 2. 修改版本 (反事实)
            Scene modifiedScene = createScene(sceneId, "Chu Chen was injured because he was Pushed down the Mountain.");
            registerScene(modifiedScene); // 重新注册，MockRepository 需要更新 Mock 行为
            
            Answer answer2 = ragService.ask(question, 3);
            String content2 = answer2.getAnswer();

            // 验证
            System.out.println("Original Answer: " + content1);
            System.out.println("Modified Answer: " + content2);
            
            assertNotEquals(content1, content2, "原文修改后，回答内容应当改变");
            assertEquals(answer1.getCitations().get(0).getChunkId(), answer2.getCitations().get(0).getChunkId(), "引用 ID 应保持一致");
            
            // 额外验证：Mock LLM 是否真的抓取了不同的名词?
            // 原文: fell, picking, herbs -> 关键词可能包含 Herbs, Picking
            // 修改: pushed, down, mountain -> 关键词可能包含 Mountain, Pushed
            // MockLlmClient 提取大写单词，如果没有大写单词，可能会 fallback。
            // 我们的 MockLlmClient 提取的是 [A-Z][a-z]+。
            // "Chu Chen" (Both), "Herbs" (Maybe not capitalized in text, let's make sure).
            // 为了让 MockLlmClient 表现明显，我们在文本里加点大写名词。
        }
        
        @Test
        @DisplayName("反事实测试 (增强版) - 确保 Mock 敏感度")
        void testCounterfactualEnhanced() {
            String question = "Who is the villain?";
            String sceneId = "chunk-villain";

            // 版本 A
            clearVectorStore();
            Scene sceneA = createScene(sceneId, "Villain is VOLDEMORT.");
            registerScene(sceneA);
            Answer ansA = ragService.ask(question, 1);

            // 版本 B
            clearVectorStore();
            Scene sceneB = createScene(sceneId, "Villain is GRINDELWALD.");
            registerScene(sceneB);
            Answer ansB = ragService.ask(question, 1);

            System.out.println("Answer A: " + ansA.getAnswer());
            System.out.println("Answer B: " + ansB.getAnswer());

            assertNotEquals(ansA.getAnswer(), ansB.getAnswer());
            assertTrue(ansA.getAnswer().contains("VOLDEMORT") || ansA.getAnswer().contains("villain")); // Mock logic dependent
            assertTrue(ansB.getAnswer().contains("GRINDELWALD") || ansB.getAnswer().contains("villain"));
        }
    }

    // === 辅助方法 ===

    private Scene createScene(String id, String content) {
        return Scene.builder()
                .id(id)
                .text(content)
                .metadata(SceneMetadata.builder()
                        .novel("Test Novel")
                        .chapterTitle("Test Chapter")
                        .build())
                .build();
    }

    private void registerScene(Scene scene) {
        // 1. Mock Repository
        Mockito.when(sceneRepository.findById(scene.getId())).thenReturn(Optional.of(scene));
        
        // 2. Mock VectorStore
        vectorStore.save(scene, embeddingService.embed(scene.getText()));
    }
}
