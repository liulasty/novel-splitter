package com.novel.splitter.core;

import com.novel.splitter.domain.model.ParagraphType;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextAwareSegmentBuilderTest {

    private final ContextAwareSegmentBuilder builder = new ContextAwareSegmentBuilder();

    @Test
    void testAdsorption_NarrationPrefix() {
        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "他问道：")); // Short Narration (prefix-like)
        inputs.add(createPara(2, "“你好。”")); // Dialogue

        List<SemanticSegment> segments = builder.build(inputs);

        // Expectation: Merged into one segment
        assertEquals(1, segments.size());
        assertEquals(2, segments.get(0).getParagraphs().size());
    }

    @Test
    void testAdsorption_NarrationSuffix() {
        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "“你好。”")); // Dialogue
        inputs.add(createPara(2, "他笑了。"));   // Short Narration (suffix)

        List<SemanticSegment> segments = builder.build(inputs);

        assertEquals(1, segments.size());
        assertEquals(2, segments.get(0).getParagraphs().size());
    }

    @Test
    void testAnchorSeparation() {
        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "前文叙述。"));
        inputs.add(createAnchor(2, "# 第一章", ParagraphType.HEADER));
        inputs.add(createPara(3, "后文叙述。"));

        List<SemanticSegment> segments = builder.build(inputs);

        // Expectation: 3 segments
        // 1. Narration
        // 2. Header
        // 3. Narration
        assertEquals(3, segments.size());
        assertEquals("HEADER", segments.get(1).getType());
    }

    @Test
    void testCodeBlockMerging() {
        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createAnchor(1, "```java", ParagraphType.CODE_BLOCK));
        inputs.add(createAnchor(2, "public class A {}", ParagraphType.CODE_BLOCK));
        inputs.add(createAnchor(3, "```", ParagraphType.CODE_BLOCK));

        List<SemanticSegment> segments = builder.build(inputs);

        // Expectation: 1 segment (CODE_BLOCK)
        assertEquals(1, segments.size());
        assertEquals("CODE_BLOCK", segments.get(0).getType());
        assertEquals(3, segments.get(0).getParagraphs().size());
    }

    private RawParagraph createPara(int index, String content) {
        return RawParagraph.builder()
                .index(index)
                .content(content)
                .isEmpty(false)
                .type(ParagraphType.TEXT)
                .build();
    }
    
    private RawParagraph createAnchor(int index, String content, ParagraphType type) {
        return RawParagraph.builder()
                .index(index)
                .content(content)
                .isEmpty(false)
                .type(type)
                .isAnchor(true)
                .build();
    }
}
