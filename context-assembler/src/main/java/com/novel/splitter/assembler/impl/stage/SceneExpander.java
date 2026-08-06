package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 1.5: 相邻块扩展 (Adjacent Expansion)
 * 按锚点 Scene 的 seq 拉取前后相邻场景补全上下文；邻居继承锚点分数 × 衰减系数。
 * 插在 ReScore 之后、Deduplicate 之前：邻居不会被重排器打低分丢弃。
 */
@Component
@RequiredArgsConstructor
public class SceneExpander {

    /** 邻居分数衰减底数：距离 1 → 0.9 */
    private static final double DECAY_BASE = 0.9;

    private final SceneRepository sceneRepository;

    public List<Scene> expand(List<Scene> scenes, AssemblerConfig config) {
        if (scenes == null || scenes.isEmpty()) {
            return scenes;
        }
        int radius = config.getExpandRadius();
        if (radius < 0) {
            return scenes;
        }
        boolean acrossChapters = config.isExpandAcrossChapters();

        Set<String> existingIds = new HashSet<>();
        for (Scene s : scenes) {
            if (s.getId() != null) {
                existingIds.add(s.getId());
            }
        }

        List<Scene> expanded = new ArrayList<>();
        for (Scene anchor : scenes) {
            expanded.add(anchor);
            Long seq = anchor.getSeq();
            if (seq == null) {
                continue;
            }
            SceneMetadata meta = anchor.getMetadata();
            if (meta == null || meta.getNovel() == null || meta.getVersion() == null
                    || meta.getChunkSize() == null || meta.getChunkOverlap() == null) {
                continue;
            }

            List<Scene> neighbors = sceneRepository.findByProfileAndSeqRange(
                    meta.getNovel(), meta.getVersion(), meta.getChunkSize(), meta.getChunkOverlap(),
                    seq - radius, seq + radius);

            double anchorScore = anchor.getScore() != null ? anchor.getScore() : 0.0;
            for (Scene neighbor : neighbors) {
                if (neighbor.getId() != null && existingIds.contains(neighbor.getId())) {
                    continue;
                }
                if (!acrossChapters && neighbor.getChapterIndex() != anchor.getChapterIndex()) {
                    continue;
                }
                if (neighbor.getSeq() == null) {
                    continue;
                }
                long distance = Math.abs(neighbor.getSeq() - seq);
                double decay = Math.pow(DECAY_BASE, distance);
                neighbor.setScore(anchorScore * decay);
                if (neighbor.getId() != null) {
                    existingIds.add(neighbor.getId());
                }
                expanded.add(neighbor);
            }
        }
        return expanded;
    }
}
