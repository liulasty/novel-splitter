package com.novel.splitter.retrieval;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.model.dto.RetrievalQuery;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.embedding.mock.MockEmbeddingService;
import com.novel.splitter.embedding.mock.MockVectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.retrieval.api.RetrievalService;
import com.novel.splitter.retrieval.impl.VectorRetrievalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

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
        
        // 使用一个 Mock 的 SceneRepository
        sceneRepository = new InMemorySceneRepository();

        // 2. 组装 Service
        retrievalService = new VectorRetrievalService(embeddingService, vectorStore, sceneRepository);

        // 3. 准备测试数据 (模拟切分后的 Scene)
        prepareTestData();
    }

    private void prepareTestData() {
        String novelName = "九阳帝尊";
        String version = "v1";

        // 创建几个模拟 Scene 并存入 Repository 和 VectorStore
        Scene s1 = createScene(novelName, version, "楚晨无法修炼", "第一章", 1);
        Scene s2 = createScene(novelName, version, "楚晨遇到小师妹", "第二章", 2);
        
        // 保存到 Repository
        sceneRepository.saveScenes(novelName, version, List.of(s1, s2));
        
        // 保存到 VectorStore (模拟 Embedding 过程)
        vectorStore.save(s1, embeddingService.embed(s1.getText()));
        vectorStore.save(s2, embeddingService.embed(s2.getText()));
    }

    private Scene createScene(String novel, String version, String text, String chapterTitle, int index) {
        return Scene.builder()
                .id(UUID.randomUUID().toString())
                .text(text)
                .chapterTitle(chapterTitle)
                .chapterIndex(index)
                .startParagraphIndex(1)
                .endParagraphIndex(10)
                .wordCount(text.length())
                .metadata(SceneMetadata.builder()
                        .novel(novel)
                        .version(version)
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
        // Map<NovelName::Version, List<Scene>>
        private final Map<String, List<Scene>> store = new HashMap<>();

        private String getKey(String novelName, String version) {
            return novelName + "::" + version;
        }

        @Override
        public void saveScenes(String novelName, String version, List<Scene> scenes) {
            store.put(getKey(novelName, version), new ArrayList<>(scenes));
        }

        @Override
        public List<Scene> loadScenes(String novelName, String version) {
            return store.getOrDefault(getKey(novelName, version), Collections.emptyList());
        }

        @Override
        public void deleteVersion(String novelName, String version) {
            store.remove(getKey(novelName, version));
        }

        @Override
        public void deleteNovel(String novelName) {
            // remove all keys starting with novelName::
            List<String> keysToRemove = new ArrayList<>();
            for (String key : store.keySet()) {
                if (key.startsWith(novelName + "::")) {
                    keysToRemove.add(key);
                }
            }
            keysToRemove.forEach(store::remove);
        }

        @Override
        public List<String> listVersions(String novelName) {
            List<String> versions = new ArrayList<>();
            for (String key : store.keySet()) {
                if (key.startsWith(novelName + "::")) {
                    versions.add(key.substring(novelName.length() + 2));
                }
            }
            return versions;
        }

        @Override
        public List<Scene> findByNovel(String novelName) {
            List<Scene> result = new ArrayList<>();
            for (Map.Entry<String, List<Scene>> entry : store.entrySet()) {
                if (entry.getKey().startsWith(novelName + "::")) {
                    result.addAll(entry.getValue());
                }
            }
            return result;
        }
    }
}
