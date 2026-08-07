package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenBudgetAllocatorTest {

    private final TokenBudgetAllocator allocator = new TokenBudgetAllocator(text -> 1000);

    private AssemblerConfig config(int maxScenes, int maxTokens) {
        AssemblerConfig c = new AssemblerConfig();
        c.setMaxScenes(maxScenes);
        c.setMaxContextTokens(maxTokens);
        return c;
    }

    private Scene scene(String id, double score) {
        return Scene.builder().id(id).text("x".repeat(10)).score(score).build();
    }

    @Test
    void tokenBudget_isHardCap_stopsWhenExceeded() {
        // 5 个场景各 1000 tokens，预算 3000 → 只应选中前 3 个（第 4 个会超预算）
        List<Scene> scenes = List.of(scene("a", 1.0), scene("b", 0.9), scene("c", 0.8),
                scene("d", 0.7), scene("e", 0.6));

        List<Scene> selected = allocator.allocate(scenes, config(10, 3000));

        assertEquals(3, selected.size());
    }

    @Test
    void maxScenes_isHardCap_stopsWhenReached() {
        List<Scene> scenes = List.of(scene("a", 1.0), scene("b", 0.9), scene("c", 0.8));

        List<Scene> selected = allocator.allocate(scenes, config(2, 10000));

        assertEquals(2, selected.size());
    }

    @Test
    void highestScoreFirst_withinBudget() {
        List<Scene> scenes = List.of(scene("low", 0.1), scene("high", 1.0), scene("mid", 0.5));

        List<Scene> selected = allocator.allocate(scenes, config(5, 2000));

        assertEquals(List.of("high", "mid"),
                selected.stream().map(Scene::getId).collect(Collectors.toList()));
    }
}
