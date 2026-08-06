package com.novel.splitter.assembler.impl.stage;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.embedding.service.OnnxRerankerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class SceneReScorerTest {

    private OnnxRerankerService reranker;
    private AssemblerConfig config;
    private SceneReScorer reScorer;

    @BeforeEach
    void setUp() {
        reranker = Mockito.mock(OnnxRerankerService.class);
        config = new AssemblerConfig();
        config.setEnableRescore(true);
        config.setEnableReranker(false); // 默认启发式路径
        reScorer = new SceneReScorer(reranker, config);
    }

    private Scene scene(double vectorScore, Double quality) {
        SceneMetadata meta = SceneMetadata.builder().build();
        if (quality != null) {
            meta.setQualityScore(quality);
        }
        return Scene.builder().text("test 正文").score(vectorScore).metadata(meta).build();
    }

    @Test
    void heuristic_qualityScoreLiftsHigherQualityScene() {
        Scene qHigh = scene(0.5, 0.2);  // 质量 0.2
        Scene qLow = scene(0.5, 0.05);  // 质量 0.05

        List<Scene> list = List.of(qHigh, qLow);
        reScorer.rescore(list, "test", config); // 问题含 "test"，关键词命中 0.1

        assertTrue(list.get(0).getScore() > list.get(1).getScore());
        // 公式：0.6*向量 + 0.2*关键词 + 0.1*实体 + 0.1*质量 - 长度惩罚
        // 两者向量0.5、关键词0.1（"test"命中）、实体0；qHigh=0.3+0.02+0.02=0.34；qLow=0.3+0.02+0.005=0.325
        assertEquals(0.34, list.get(0).getScore(), 1e-9);
        assertEquals(0.325, list.get(1).getScore(), 1e-9);
    }

    @Test
    void onnx_blendsQualityWithConfiguredWeight() {
        config.setEnableReranker(true);
        when(reranker.isAvailable()).thenReturn(true);
        when(reranker.rerank(anyString(), anyList())).thenReturn(List.of(0.8f, 0.8f));

        Scene qHigh = scene(0.0, 0.9);
        Scene qLow = scene(0.0, 0.1);

        List<Scene> list = List.of(qHigh, qLow);
        reScorer.rescore(list, "q", config);

        // w=0.15：qHigh=0.8*0.85+0.9*0.15=0.815；qLow=0.8*0.85+0.1*0.15=0.695
        // 0.8f→double 有 ~1.2e-8 精度误差，delta 放宽到 1e-6
        assertEquals(0.815, list.get(0).getScore(), 1e-6);
        assertEquals(0.695, list.get(1).getScore(), 1e-6);
    }

    @Test
    void onnx_skipsQualityWhenNotComputed() {
        config.setEnableReranker(true);
        when(reranker.isAvailable()).thenReturn(true);
        when(reranker.rerank(anyString(), anyList())).thenReturn(List.of(0.8f));

        Scene s = scene(0.0, SceneMetadata.SCORE_NOT_COMPUTED);
        List<Scene> list = List.of(s);
        reScorer.rescore(list, "q", config);

        assertEquals(0.8, list.get(0).getScore(), 1e-6);
    }

    @Test
    void heuristic_entityScoreHitsWhenCharactersPopulated() {
        SceneMetadata meta = SceneMetadata.builder().characters(List.of("萧炎")).build();
        Scene s1 = scene(0.5, null);
        s1.setMetadata(meta);
        List<Scene> list = List.of(s1);

        reScorer.rescore(list, "萧炎", config);

        // 0.6*0.5 + 0.2*0（关键词未命中） + 0.1*0.1（实体命中1个） + 0.1*0（质量未计算） - 0 = 0.31
        assertEquals(0.31, list.get(0).getScore(), 1e-9);
    }
}
