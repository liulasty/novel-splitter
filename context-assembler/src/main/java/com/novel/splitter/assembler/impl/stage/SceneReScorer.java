package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.embedding.service.OnnxRerankerService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stage 1: 重评分 (ReScore)
 * <p>
 * 使用 ONNX 重排模型 (bge-reranker-base) 进行深度语义相关性打分，
 * 当重排模型不可用或配置关闭时，回退到启发式评分（关键词+实体命中）。
 * </p>
 */
@Slf4j
@Component
public class SceneReScorer {

    private final OnnxRerankerService rerankerService;
    private final AssemblerConfig config;

    public SceneReScorer(OnnxRerankerService rerankerService, AssemblerConfig config) {
        this.rerankerService = rerankerService;
        this.config = config;
    }

    @PostConstruct
    public void init() {
        log.info("Reranker switch status: enable={}, serviceAvailable={}",
                config.isEnableReranker(), rerankerService.isAvailable());
    }

    public void rescore(List<Scene> scenes, String question, AssemblerConfig config) {
        if (!config.isEnableRescore()) {
            return;
        }

        if (config.isEnableReranker() && rerankerService.isAvailable()) {
            rerankWithOnnx(scenes, question);
        } else {
            rerankWithHeuristic(scenes, question);
        }
    }

    /**
     * ONNX 重排模型打分
     */
    private void rerankWithOnnx(List<Scene> scenes, String question) {
        List<String> texts = scenes.stream()
                .map(Scene::getText)
                .collect(Collectors.toList());

        List<Float> scores;
        try {
            scores = rerankerService.rerank(question, texts);
        } catch (Exception e) {
            log.warn("ONNX reranker failed, falling back to heuristic scoring", e);
            rerankWithHeuristic(scenes, question);
            return;
        }

        for (int i = 0; i < scenes.size() && i < scores.size(); i++) {
            double rerank = scores.get(i);
            double q = qualityScoreOf(scenes.get(i));
            double w = config.getQualityScoreWeight();
            if (w > 0 && q > 0) {
                scenes.get(i).setScore(rerank * (1 - w) + q * w);
            } else {
                scenes.get(i).setScore(rerank);
            }
        }
    }

    /**
     * 启发式规则打分（原逻辑，作为降级兜底）
     */
    private void rerankWithHeuristic(List<Scene> scenes, String question) {
        List<String> keywords = extractKeywords(question);

        for (Scene scene : scenes) {
            double vectorScore = scene.getScore() != null ? scene.getScore() : 0.0;
            double keywordScore = calculateKeywordScore(scene.getText(), keywords);
            double entityScore = calculateEntityScore(scene.getMetadata(), keywords);
            double lengthPenalty = calculateLengthPenalty(scene.getText());

            double quality = qualityScoreOf(scene);
            double finalScore = (vectorScore * 0.6) + (keywordScore * 0.2)
                    + (entityScore * 0.1) + (quality * 0.1) - lengthPenalty;
            if (finalScore < 0) finalScore = 0;

            scene.setScore(finalScore);
        }
    }

    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        if (text == null || text.isEmpty()) return keywords;

        // 1. 英文单词
        Pattern enPattern = Pattern.compile("[a-zA-Z0-9]+");
        Matcher enMatcher = enPattern.matcher(text);
        while (enMatcher.find()) {
            keywords.add(enMatcher.group());
        }

        // 2. 中文 Bigram (二元分词)
        Pattern cnPattern = Pattern.compile("[\\u4e00-\\u9fa5]+");
        Matcher cnMatcher = cnPattern.matcher(text);
        while (cnMatcher.find()) {
            String cnBlock = cnMatcher.group();
            if (cnBlock.length() < 2) {
                // 如果整个问题很短，加入单字
                if (text.length() <= 2) keywords.add(cnBlock);
                continue;
            }
            // 生成 bigram
            for (int i = 0; i < cnBlock.length() - 1; i++) {
                keywords.add(cnBlock.substring(i, i + 2));
            }
        }

        return keywords;
    }

    private double calculateKeywordScore(String content, List<String> keywords) {
        if (content == null || keywords == null || keywords.isEmpty()) return 0.0;
        int hits = 0;
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                hits++;
            }
        }
        // 简单归一化: 每个命中 +0.1，上限 1.0
        return Math.min(hits * 0.1, 1.0);
    }

    private double calculateEntityScore(SceneMetadata metadata, List<String> keywords) {
        if (metadata == null || metadata.getCharacters() == null || keywords == null || keywords.isEmpty()) return 0.0;
        int hits = 0;
        for (String charName : metadata.getCharacters()) {
            for (String keyword : keywords) {
                if (charName.contains(keyword) || keyword.contains(charName)) {
                    hits++;
                }
            }
        }
        return Math.min(hits * 0.1, 1.0);
    }
    
    private double calculateLengthPenalty(String content) {
        if (content == null) return 0.0;
        // 超过 2000 字开始惩罚
        if (content.length() > 2000) {
            return (content.length() - 2000) * 0.0001;
        }
        return 0.0;
    }

    /**
     * 质量分；未计算（<= SCORE_NOT_COMPUTED=-1）或无值时返回 0，不参与混合。
     * 启发式路径权重固定 0.1，不受 config.qualityScoreWeight 影响。
     */
    private double qualityScoreOf(Scene scene) {
        if (scene == null || scene.getMetadata() == null || scene.getMetadata().getQualityScore() == null) {
            return 0.0;
        }
        double q = scene.getMetadata().getQualityScore();
        return q <= SceneMetadata.SCORE_NOT_COMPUTED ? 0.0 : q;
    }
}
