package com.novel.splitter.assembler.impl;

import com.novel.splitter.assembler.api.ContextAssembler;
import com.novel.splitter.assembler.model.AssembledContext;
import com.novel.splitter.assembler.model.ContextBlock;
import com.novel.splitter.assembler.support.TokenCounter;
import com.novel.splitter.domain.model.Scene;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StandardContextAssembler implements ContextAssembler {

    private final TokenCounter tokenCounter;

    @Override
    public AssembledContext assemble(List<Scene> retrievedChunks, int maxTokens) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return AssembledContext.builder()
                    .blocks(Collections.emptyList())
                    .totalTokens(0)
                    .truncated(false)
                    .build();
        }

        // 1. 同一 chapterIndex 去重 (保留第一次出现的)
        List<Scene> deduplicated = deduplicateByChapterIndex(retrievedChunks);

        // 2. 按 chapterIndex + paragraphIndex 排序
        deduplicated.sort(Comparator.comparingInt(Scene::getChapterIndex)
                .thenComparingInt(Scene::getStartParagraphIndex));

        // 3. 按 Token 上限截断
        List<Scene> truncatedList = new ArrayList<>();
        int currentTokens = 0;
        boolean isTruncated = false;

        for (Scene scene : deduplicated) {
            int sceneTokens = tokenCounter.count(scene.getText());
            // 如果加入当前 Scene 会超限，则停止
            if (currentTokens + sceneTokens > maxTokens) {
                isTruncated = true;
                break;
            }
            truncatedList.add(scene);
            currentTokens += sceneTokens;
        }

        // 4. 生成稳定编号 (C1, C2...) 并转换为 ContextBlock
        List<ContextBlock> blocks = new ArrayList<>();
        for (int i = 0; i < truncatedList.size(); i++) {
            Scene scene = truncatedList.get(i);
            blocks.add(ContextBlock.builder()
                    .id("C" + (i + 1))
                    .content(scene.getText())
                    .chapterIndex(scene.getChapterIndex())
                    .paragraphIndex(scene.getStartParagraphIndex())
                    .build());
        }

        return AssembledContext.builder()
                .blocks(blocks)
                .totalTokens(currentTokens)
                .truncated(isTruncated)
                .build();
    }

    private List<Scene> deduplicateByChapterIndex(List<Scene> scenes) {
        Set<Integer> seenChapters = new HashSet<>();
        List<Scene> result = new ArrayList<>();
        
        for (Scene scene : scenes) {
            // Scene.chapterIndex is primitive int, so no null check needed
            if (!seenChapters.contains(scene.getChapterIndex())) {
                seenChapters.add(scene.getChapterIndex());
                result.add(scene);
            }
        }
        return result;
    }
}
