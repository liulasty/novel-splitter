package com.novel.splitter.assembler.impl;

import com.novel.splitter.assembler.config.AssemblerConfig;
import com.novel.splitter.assembler.impl.stage.*;
import com.novel.splitter.assembler.support.TokenCounter;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class StandardContextAssemblerTest {

    private TokenCounter tokenCounter;
    private StandardContextAssembler assembler;
    private AssemblerConfig config;

    @BeforeEach
    void setUp() {
        tokenCounter = Mockito.mock(TokenCounter.class);
        config = new AssemblerConfig();
        config.setMaxContextTokens(100);
        config.setEnableMerge(false); // Default false for simple tests
        config.setEnableRescore(false);
        config.setMaxScenes(5);

        SceneReScorer reScorer = new SceneReScorer();
        SceneDeduplicator deduplicator = new SceneDeduplicator();
        SceneMerger merger = new SceneMerger(tokenCounter);
        TokenBudgetAllocator allocator = new TokenBudgetAllocator(tokenCounter);

        assembler = new StandardContextAssembler(reScorer, deduplicator, merger, allocator, tokenCounter);
    }

    private Scene createScene(String id, String text, int chapterIndex, int paragraphIndex, double score) {
        return Scene.builder()
                .id(id)
                .text(text)
                .chapterIndex(chapterIndex)
                .startParagraphIndex(paragraphIndex)
                .score(score)
                .metadata(SceneMetadata.builder()
                        .novel("TestNovel")
                        .chapterIndex(chapterIndex)
                        .build())
                .build();
    }

    @Test
    void testAssemble_BasicFlow() {
        when(tokenCounter.count(anyString())).thenReturn(10);

        List<Scene> input = Arrays.asList(
                createScene("1", "S1", 1, 1, 0.9),
                createScene("2", "S2", 2, 1, 0.8)
        );

        List<ContextBlock> result = assembler.assemble("q", input, config);

        assertEquals(2, result.size());
        assertEquals("1", result.get(0).getChunkId()); // Sorted by doc order
        assertEquals("2", result.get(1).getChunkId());
    }

    @Test
    void testAssemble_Deduplication() {
        when(tokenCounter.count(anyString())).thenReturn(10);

        List<Scene> input = Arrays.asList(
                createScene("1", "S1", 1, 1, 0.9),
                createScene("1", "S1_Dup", 1, 1, 0.8) // Duplicate ID
        );

        List<ContextBlock> result = assembler.assemble("q", input, config);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getChunkId());
    }

    @Test
    void testAssemble_TokenBudget() {
        // S1: 60 tokens, S2: 50 tokens. Max 100.
        // Sorted by score: S1 (0.9), S2 (0.8).
        // Select S1 (60). Total 60.
        // Try S2 (50). Total 110 > 100. Skip S2.
        
        when(tokenCounter.count("S1")).thenReturn(60);
        when(tokenCounter.count("S2")).thenReturn(50);

        List<Scene> input = Arrays.asList(
                createScene("1", "S1", 1, 1, 0.9),
                createScene("2", "S2", 2, 1, 0.8)
        );

        List<ContextBlock> result = assembler.assemble("q", input, config);

        assertEquals(1, result.size());
        assertEquals("1", result.get(0).getChunkId());
    }
    
    @Test
    void testAssemble_Rescore_Chinese() {
        config.setEnableRescore(true);
        when(tokenCounter.count(anyString())).thenReturn(10);

        // Scene 1: 包含关键词 "萧炎"
        Scene s1 = createScene("1", "萧炎来到了乌坦城。", 1, 1, 0.5);
        // Scene 2: 不包含关键词
        Scene s2 = createScene("2", "这里是一片荒芜之地。", 1, 2, 0.5);

        List<Scene> input = Arrays.asList(s1, s2);

        // 问题包含 "萧炎"
        List<ContextBlock> result = assembler.assemble("萧炎在哪里？", input, config);

        assertEquals(2, result.size());
        
        // s1 应该因为关键词命中而分数更高
        // 原始 0.5 -> 0.5 * 0.6 = 0.3
        // s1 keyword hit "萧炎" -> +0.1 -> total 0.4
        // s2 keyword hit 0 -> total 0.3
        
        // 验证 ContextBlock 中的分数
        // 注意：ContextBlock 列表最终是按文档顺序排序的，但我们可以检查分数
        ContextBlock b1 = result.stream().filter(b -> b.getChunkId().equals("1")).findFirst().orElseThrow();
        ContextBlock b2 = result.stream().filter(b -> b.getChunkId().equals("2")).findFirst().orElseThrow();

        assertTrue(b1.getScore() > b2.getScore(), "Scene 1 should have higher score due to keyword match");
    }

    @Test
    void testAssemble_Merge() {
        config.setEnableMerge(true);
        config.setMaxChunkLength(1000);
        
        when(tokenCounter.count(anyString())).thenReturn(10); // Any small token count

        // Adjacent scenes: Ch1 P1, Ch1 P2 (assuming adjacent logic works)
        Scene s1 = createScene("1", "Part1", 1, 1, 0.9);
        s1.setEndParagraphIndex(1);
        
        Scene s2 = createScene("2", "Part2", 1, 2, 0.8);
        s2.setStartParagraphIndex(2); // Adjacent to s1 end(1) + 1 = 2
        s2.setEndParagraphIndex(2);
        
        List<Scene> input = Arrays.asList(s1, s2);

        List<ContextBlock> result = assembler.assemble("q", input, config);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getContent().contains("Part1"));
        assertTrue(result.get(0).getContent().contains("Part2"));
        // Check if merged IDs are present
        assertNotNull(result.get(0).getMetadata().get("mergedSceneIds"));
    }
}
