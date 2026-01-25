package com.novel.splitter.retrieval;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.embedding.mock.MockEmbeddingService;
import com.novel.splitter.embedding.mock.MockVectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.repository.impl.LocalFileSceneRepository;
import com.novel.splitter.retrieval.api.RetrievalQuery;
import com.novel.splitter.retrieval.api.RetrievalService;
import com.novel.splitter.retrieval.impl.VectorRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 离线验证脚本
 * <p>
 * 模拟 RAG 检索流程，验证链路打通和元数据完整性。
 * </p>
 */
class RetrievalQualityTest {

    private RetrievalService retrievalService;
    private SceneRepository sceneRepository;
    private VectorStore vectorStore;
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        // 1. 初始化 Mock 组件
        embeddingService = new MockEmbeddingService(768);
        vectorStore = new MockVectorStore();
        
        // 使用一个 Mock 的 SceneRepository 或者真实的 LocalFileSceneRepository
        // 这里为了简单，我们扩展 LocalFileSceneRepository 或者直接 Mock 它
        // 由于 LocalFileSceneRepository 依赖文件系统，测试环境可能没有文件
        // 所以我们手动 Mock 一个带数据的 Repository
        sceneRepository = new InMemorySceneRepository();

        // 2. 组装 Service
        retrievalService = new VectorRetrievalService(embeddingService, vectorStore, sceneRepository);

        // 3. 准备测试数据 (模拟切分后的 Scene)
        prepareTestData();
    }

    private void prepareTestData() {
        // 创建几个模拟 Scene 并存入 Repository 和 VectorStore
        Scene s1 = createScene("楚晨无法修炼", "第一章", 1);
        Scene s2 = createScene("楚晨遇到小师妹", "第二章", 2);
        
        // 保存到 Repository
        sceneRepository.saveScenes("test-novel", "v1", List.of(s1, s2));
        
        // 保存到 VectorStore (模拟 Embedding 过程)
        vectorStore.save(s1, embeddingService.embed(s1.getText()));
        vectorStore.save(s2, embeddingService.embed(s2.getText()));
    }

    private Scene createScene(String text, String chapterTitle, int index) {
        return Scene.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .chapterTitle(chapterTitle)
                .chapterIndex(index)
                .startParagraphIndex(1)
                .endParagraphIndex(10)
                .wordCount(text.length())
                .metadata(SceneMetadata.builder()
                        .novel("九阳帝尊")
                        .chapterTitle(chapterTitle)
                        .chapterIndex(index)
                        .role("narration")
                        .build())
                .build();
    }

    @Test
    void retrieval_quality_check() {
        List<String> queries = List.of(
                "楚晨为什么无法修炼？",
                "小师妹是谁？"
        );

        System.out.println("=== 开始检索质量检查 (Mock Mode) ===");
        
        for (String q : queries) {
            System.out.println("\nQuery: " + q);
            
            // 执行检索
            List<Scene> result = retrievalService.retrieve(RetrievalQuery.builder()
                    .question(q)
                    .topK(3)
                    .build());
            
            if (result.isEmpty()) {
                System.out.println(" [WARN] No results found.");
            } else {
                result.forEach(c -> {
                    System.out.println(" - [Chunk] ID=" + c.getId().substring(0, 8) + "...");
                    System.out.println("   Metadata: " + c.getMetadata());
                    System.out.println("   Text Preview: " + c.getText().substring(0, Math.min(20, c.getText().length())));
                });
            }
        }
        
        System.out.println("\n=== 检查完成 ===");
    }

    /**
     * 简单的内存 Repository 用于测试
     */
    static class InMemorySceneRepository implements SceneRepository {
        private final java.util.Map<String, Scene> store = new java.util.HashMap<>();

        @Override
        public void saveScenes(String novelName, String version, List<Scene> scenes) {
            scenes.forEach(s -> store.put(s.getId(), s));
        }

        @Override
        public java.util.Optional<Scene> findById(String id) {
            return java.util.Optional.ofNullable(store.get(id));
        }
    }
}
