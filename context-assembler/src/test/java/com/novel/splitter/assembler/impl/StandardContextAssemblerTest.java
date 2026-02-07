package com.novel.splitter.assembler.impl;

import com.novel.splitter.domain.model.context.AssembledContext;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.assembler.support.TokenCounter;
import com.novel.splitter.domain.model.Scene;
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

    @BeforeEach
    void setUp() {
        tokenCounter = Mockito.mock(TokenCounter.class);
        assembler = new StandardContextAssembler(tokenCounter);
    }

    private Scene createScene(String text, int chapterIndex, int paragraphIndex) {
        return Scene.builder()
                .text(text)
                .chapterIndex(chapterIndex)
                .startParagraphIndex(paragraphIndex)
                .metadata(com.novel.splitter.domain.model.SceneMetadata.builder()
                        .chapterIndex(chapterIndex)
                        .build())
                .build();
    }

    @Test
    void testAssemble_Deduplication() {
        // Mock token count: each scene = 10 tokens
        when(tokenCounter.count(anyString())).thenReturn(10);

        List<Scene> input = Arrays.asList(
                createScene("S1", 1, 1),
                createScene("S2", 1, 5), // Duplicate chapter
                createScene("S3", 2, 1)
        );

        AssembledContext result = assembler.assemble(input, 100);

        assertEquals(2, result.getBlocks().size());
        assertEquals(1, result.getBlocks().get(0).getSceneMetadata().getChapterIndex());
        assertEquals(2, result.getBlocks().get(1).getSceneMetadata().getChapterIndex());
        // Verify stable IDs
        assertEquals("C1", result.getBlocks().get(0).getChunkId());
        assertEquals("C2", result.getBlocks().get(1).getChunkId());
    }

    @Test
    void testAssemble_Sorting() {
        when(tokenCounter.count(anyString())).thenReturn(10);

        List<Scene> input = Arrays.asList(
                createScene("S3", 3, 1),
                createScene("S1", 1, 10),
                createScene("S2", 2, 5)
        );

        AssembledContext result = assembler.assemble(input, 100);

        assertEquals(3, result.getBlocks().size());
        assertEquals(1, result.getBlocks().get(0).getSceneMetadata().getChapterIndex());
        assertEquals(2, result.getBlocks().get(1).getSceneMetadata().getChapterIndex());
        assertEquals(3, result.getBlocks().get(2).getSceneMetadata().getChapterIndex());
        
        assertEquals("C1", result.getBlocks().get(0).getChunkId());
        assertEquals("C2", result.getBlocks().get(1).getChunkId());
        assertEquals("C3", result.getBlocks().get(2).getChunkId());
    }

    @Test
    void testAssemble_Truncation() {
        // Scene A: 50 tokens
        // Scene B: 60 tokens
        // Limit: 100 tokens
        // Expect: Only Scene A (50 < 100), adding B (50+60=110 > 100) -> Drop B
        
        when(tokenCounter.count("A")).thenReturn(50);
        when(tokenCounter.count("B")).thenReturn(60);

        List<Scene> input = Arrays.asList(
                createScene("A", 1, 1),
                createScene("B", 2, 1)
        );

        AssembledContext result = assembler.assemble(input, 100);

        assertEquals(1, result.getBlocks().size());
        assertEquals("A", result.getBlocks().get(0).getContent());
        assertTrue(result.isTruncated());
    }

    @Test
    void testAssemble_StableIds_OrderIndependence() {
        when(tokenCounter.count(anyString())).thenReturn(10);

        List<Scene> input1 = Arrays.asList(
                createScene("S1", 1, 1),
                createScene("S2", 2, 1)
        );

        List<Scene> input2 = Arrays.asList(
                createScene("S2", 2, 1),
                createScene("S1", 1, 1)
        );

        AssembledContext result1 = assembler.assemble(input1, 100);
        AssembledContext result2 = assembler.assemble(input2, 100);

        // Both should be sorted to [S1, S2] -> C1, C2
        assertEquals(result1.getBlocks().size(), result2.getBlocks().size());
        
        assertEquals("C1", result1.getBlocks().get(0).getChunkId());
        assertEquals(1, result1.getBlocks().get(0).getSceneMetadata().getChapterIndex());
        
        assertEquals("C1", result2.getBlocks().get(0).getChunkId());
        assertEquals(1, result2.getBlocks().get(0).getSceneMetadata().getChapterIndex());
        
        assertEquals("C2", result1.getBlocks().get(1).getChunkId());
        assertEquals(2, result1.getBlocks().get(1).getSceneMetadata().getChapterIndex());
        
        assertEquals("C2", result2.getBlocks().get(1).getChunkId());
        assertEquals(2, result2.getBlocks().get(1).getSceneMetadata().getChapterIndex());
    }
}
