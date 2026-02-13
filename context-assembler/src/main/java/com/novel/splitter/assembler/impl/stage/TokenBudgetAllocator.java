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
            // 数量限制
            if (selected.size() >= maxScenes) {
                break;
            }

            int sceneTokens = tokenCounter.count(scene.getText());
            
            // 预算检查
            if (currentTokens + sceneTokens <= maxTokens) {
                selected.add(scene);
                currentTokens += sceneTokens;
            } else {
                // 如果是第一个且超长，可能需要截断 (此处暂不处理截断，直接跳过或仅保留这一个)
                // 策略：如果一个都没选且这个超长，还是选上（并在后续截断），或者严格丢弃。
                // 这里采用严格丢弃，防止爆 Token
                if (selected.isEmpty() && maxTokens > 0) {
                     // 极其特殊情况：单个 Scene 比整个窗口还大。
                     // 可以在此做截断，但 Scene 应该是完整的。
                     // 暂且跳过。
                     log.warn("Scene {} ({} tokens) exceeds max context tokens ({}), skipped.", scene.getId(), sceneTokens, maxTokens);
                }
            }
        }
        
        return selected;
    }
}
