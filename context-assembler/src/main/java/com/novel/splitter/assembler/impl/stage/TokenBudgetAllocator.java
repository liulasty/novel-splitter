package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.assembler.support.TokenCounter;
import com.novel.splitter.domain.model.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stage 4: Token 预算控制 (Token Budget Control)
 * <p>
 * 基于评分优先级和 Token 预算筛选 Scene。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenBudgetAllocator {

    private final TokenCounter tokenCounter;

    public List<Scene> allocate(List<Scene> scenes, AssemblerConfig config) {
        if (scenes == null || scenes.isEmpty()) {
            return new ArrayList<>();
        }

        int maxTokens = config.getMaxContextTokens();
        int maxScenes = config.getMaxScenes();

        // 1. 按分数降序 (优先保留高分)
        List<Scene> sortedByScore = new ArrayList<>(scenes);
        sortedByScore.sort(Comparator.comparingDouble((Scene s) -> 
            s.getScore() != null ? s.getScore() : 0.0).reversed());

        List<Scene> selected = new ArrayList<>();
        int currentTokens = 0;

        for (Scene scene : sortedByScore) {
            // 数量限制（硬约束）：达到 maxScenes 即停止
            if (selected.size() >= maxScenes) {
                break;
            }

            int sceneTokens = tokenCounter.count(scene.getText());

            // Token 预算（硬约束）：任一达到上限即停止，保证上下文 ≤ maxContextTokens
            if (currentTokens + sceneTokens > maxTokens) {
                log.warn("跳过场景 {}（{} tokens）：超出最大上下文 token 数（{}），"
                        + "已选 {}/{} 场景、当前 {} tokens。",
                        scene.getId(), sceneTokens, maxTokens, selected.size(), maxScenes, currentTokens);
                break;
            }

            selected.add(scene);
            currentTokens += sceneTokens;
        }

        return selected;
    }
}
