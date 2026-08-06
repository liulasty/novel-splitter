package com.novel.splitter.assembler.impl;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.assembler.impl.stage.SceneDeduplicator;
import com.novel.splitter.assembler.impl.stage.SceneExpander;
import com.novel.splitter.assembler.impl.stage.SceneMerger;
import com.novel.splitter.assembler.impl.stage.SceneReScorer;
import com.novel.splitter.assembler.impl.stage.TokenBudgetAllocator;
import com.novel.splitter.assembler.support.TokenCounter;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Primary
@RequiredArgsConstructor
@Slf4j
public class StandardContextAssembler implements ContextAssembler {

    private final SceneReScorer reScorer;
    private final SceneDeduplicator deduplicator;
    private final SceneMerger merger;
    private final TokenBudgetAllocator allocator;
    private final TokenCounter tokenCounter;
    private final SceneExpander expander;

    @Override
    public List<ContextBlock> assemble(String question, List<Scene> retrievedScenes, AssemblerConfig config) {
        int originalCount = retrievedScenes != null ? retrievedScenes.size() : 0;
        if (originalCount == 0) {
            return Collections.emptyList();
        }

        // Stage 1: ReScore
        // 注意：ReScore 会直接修改 Scene 对象的 score 字段
        reScorer.rescore(retrievedScenes, question, config);

        // Stage 1.5: 相邻块扩展（邻居继承锚点衰减分，插入在去重/合并前）
        retrievedScenes = expander.expand(retrievedScenes, config);

        // Stage 2: Deduplicate
        List<Scene> uniqueScenes = deduplicator.deduplicate(retrievedScenes);
        int dedupCount = uniqueScenes.size();

        // Stage 3: Merge Adjacent
        List<Scene> mergedScenes = merger.merge(uniqueScenes, config);
        int mergedCount = mergedScenes.size();

        // Stage 4: Token Budget Control
        List<Scene> finalScenes = allocator.allocate(mergedScenes, config);
        int finalCount = finalScenes.size();
        
        // Calculate stats
        int totalTokens = finalScenes.stream().mapToInt(s -> tokenCounter.count(s.getText())).sum();
        int truncatedCount = mergedCount - finalCount;

        // Stage 5: Final Sort & Build
        // 5.1 计算 Rank (基于 Score 在 finalScenes 中的排名)
        List<Scene> rankedScenes = new ArrayList<>(finalScenes);
        rankedScenes.sort(Comparator.comparingDouble((Scene s) -> s.getScore() != null ? s.getScore() : 0.0).reversed());
        
        Map<String, Integer> rankMap = new HashMap<>();
        for (int i = 0; i < rankedScenes.size(); i++) {
            rankMap.put(rankedScenes.get(i).getId(), i + 1);
        }

        // 5.2 按文档顺序 (Chapter -> Paragraph) 排序，以便 LLM 阅读
        finalScenes.sort(Comparator.comparingInt(Scene::getChapterIndex)
                .thenComparingInt(Scene::getStartParagraphIndex));

        List<ContextBlock> blocks = new ArrayList<>();
        Scene prev = null;
        for (Scene scene : finalScenes) {
            int tokens = tokenCounter.count(scene.getText());
            int rank = rankMap.getOrDefault(scene.getId(), 0);

            // 与上一块有段落 gap 的孤立块携带 prefixContext，供 LLM 序列化补上文
            boolean isolated = prev != null && isGap(prev, scene);

            Map<String, Object> metadata = new HashMap<>();
            if (scene.getMetadata() != null) {
                metadata.put("novelName", scene.getMetadata().getNovel());
                metadata.put("chapterTitle", scene.getMetadata().getChapterTitle());
                metadata.put("chapterIndex", scene.getMetadata().getChapterIndex());
                if (scene.getMetadata().getExtra() != null) {
                    metadata.putAll(scene.getMetadata().getExtra());
                }
            }
            // Ensure mergedSceneIds is visible
            if (scene.getMetadata() != null && scene.getMetadata().getExtra() != null) {
                Object mergedIds = scene.getMetadata().getExtra().get("mergedSceneIds");
                if (mergedIds != null) {
                    metadata.put("mergedSceneIds", mergedIds);
                }
            }

            blocks.add(ContextBlock.builder()
                    .chunkId(scene.getId())
                    .content(scene.getText())
                    .prefixContext(isolated ? scene.getPrefixContext() : null)
                    .sceneMetadata(scene.getMetadata())
                    .tokenCount(tokens)
                    .rank(rank)
                    .score(scene.getScore() != null ? scene.getScore() : 0.0)
                    .metadata(metadata)
                    .build());
            prev = scene;
        }

        // Log Output
        log.info("\n[Assembler]\n" +
                "- 原始检索数量: {}\n" +
                "- 去重后数量: {}\n" +
                "- 合并后数量: {}\n" +
                "- 最终使用数量: {}\n" +
                "- 总Token: {}\n" +
                "- 被截断数量: {}",
                originalCount, dedupCount, mergedCount, finalCount, totalTokens, truncatedCount);

        return blocks;
    }

    /** 孤立块：与上一块首尾有段落 gap（paragraphIndex 不连续），需要前缀补上下文 */
    private boolean isGap(Scene prev, Scene cur) {
        return cur.getStartParagraphIndex() > prev.getEndParagraphIndex() + 1;
    }
}
