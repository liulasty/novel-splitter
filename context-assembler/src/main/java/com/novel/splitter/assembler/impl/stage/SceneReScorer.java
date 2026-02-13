package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 1: 重评分 (ReScore)
 * <p>
 * 综合向量分数、关键词命中、实体命中等多维度进行重排序。
 * </p>
 */
@Component
public class SceneReScorer {

    public void rescore(List<Scene> scenes, String question, AssemblerConfig config) {
        if (!config.isEnableRescore()) {
            return;
        }

        // 改进分词: 提取英文单词和中文词(2字以上)
        List<String> keywords = extractKeywords(question);

        for (Scene scene : scenes) {
            double vectorScore = scene.getScore() != null ? scene.getScore() : 0.0;
            double keywordScore = calculateKeywordScore(scene.getText(), keywords);
            double entityScore = calculateEntityScore(scene.getMetadata(), keywords);
            double lengthPenalty = calculateLengthPenalty(scene.getText());

            // 简单评分公式：vector * 0.6 + keyword * 0.2 + entity * 0.2 - penalty
            double finalScore = (vectorScore * 0.6) + (keywordScore * 0.2) + (entityScore * 0.2) - lengthPenalty;
            
            // 确保分数非负 (可选)
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

        // DEBUG LOG
        // System.out.println("DEBUG: Question='" + text + "', Extracted keywords=" + keywords);
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
        // DEBUG LOG
        // System.out.println("DEBUG: Content='" + (content.length() > 20 ? content.substring(0,20)+"..." : content) + "', Hits=" + hits);
        
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
}
