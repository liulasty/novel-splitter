package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.domain.model.Scene;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Stage 2: 去重 (Deduplicate)
 * <p>
 * 防止同一 Scene 多版本或重复 Chunk 污染上下文。
 * </p>
 */
@Component
public class SceneDeduplicator {

    public List<Scene> deduplicate(List<Scene> scenes) {
        if (scenes == null || scenes.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 ID 去重，保留第一次出现的 (假设输入已按分数排序)
        // 或者保留分数最高的 (如果输入未排序，应先排序)
        // 这里假设调用方可能会在 ReScore 后重排，所以我们先按分数排序
        
        // 1. 按分数降序
        List<Scene> sortedScenes = new ArrayList<>(scenes);
        sortedScenes.sort(Comparator.comparingDouble(Scene::getScore).reversed());

        // 2. 去重
        Set<String> seenIds = new HashSet<>();
        List<Scene> result = new ArrayList<>();

        for (Scene scene : sortedScenes) {
            // 如果 ID 为空，降级使用 chapterIndex + paragraphIndex 作为唯一键
            String uniqueKey = scene.getId();
            if (uniqueKey == null) {
                uniqueKey = scene.getChapterIndex() + "_" + scene.getStartParagraphIndex();
            }

            if (!seenIds.contains(uniqueKey)) {
                seenIds.add(uniqueKey);
                result.add(scene);
            }
        }

        return result;
    }
}
