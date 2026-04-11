package com.novel.splitter.validation.core;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;

import java.util.List;

/**
 * 在入库前写入 {@link SceneMetadata#getQualityScore()}，与长度等校验规则对齐，保证非 null。
 */
public final class SceneQualityScoreWriter {

    private SceneQualityScoreWriter() {
    }

    /**
     * @param minLength 过短惩罚阈值（与切分 min-length 一致）
     * @param maxLength 过长惩罚阈值（与动态窗口上限语义对齐）
     */
    public static void apply(List<Scene> scenes, int minLength, int maxLength) {
        if (scenes == null || scenes.isEmpty()) {
            return;
        }
        int min = Math.max(1, minLength);
        int max = Math.max(min + 1, maxLength);
        for (Scene scene : scenes) {
            SceneMetadata meta = scene.getMetadata();
            if (meta == null) {
                meta = new SceneMetadata();
                scene.setMetadata(meta);
            }
            meta.setQualityScore(computeScore(scene, min, max));
        }
    }

    private static double computeScore(Scene scene, int minLength, int maxLength) {
        String text = scene.getText();
        int len = text != null ? text.length() : 0;
        double score = 1.0;
        if (len < minLength) {
            score *= Math.max(0.35, (double) len / minLength);
        } else if (len > maxLength) {
            score *= Math.max(0.5, (double) maxLength / len);
        }
        if (text != null) {
            String trimmed = text.stripTrailing();
            if (!trimmed.isEmpty()) {
                char lastChar = trimmed.charAt(trimmed.length() - 1);
                if (lastChar != '。' && lastChar != '”' && lastChar != '！' && lastChar != '？'
                        && lastChar != '.' && lastChar != '}') {
                    score *= 0.85;
                }
            }
        }
        return clamp(score, 0.05, 1.0);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
