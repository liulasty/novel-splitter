package com.novel.splitter.core;

import com.novel.splitter.domain.model.ParagraphType;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;
import com.novel.splitter.embedding.api.EmbeddingService;
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

    @Test
    void testSemanticMergeWithEmbedding() {
        EmbeddingService mockEmbeddingService = new EmbeddingService() {
            @Override
            public List<float[]> embedBatch(List<String> texts) {
                List<float[]> result = new ArrayList<>();
                for (String text : texts) {
                    if (text.contains("A")) {
                        result.add(new float[]{1.0f, 0.0f, 0.0f}); // Simulating high similarity for A and B if we change B
                    } else if (text.contains("B")) {
                        result.add(new float[]{0.9f, 0.1f, 0.0f}); // Cosine similarity ~ 0.99 > 0.85
                    } else {
                        result.add(new float[]{0.0f, 1.0f, 0.0f});
                    }
                }
                return result;
            }
        };
        ContextAwareSegmentBuilder semanticBuilder = new ContextAwareSegmentBuilder(mockEmbeddingService);

        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "这是段落A，描述同一个场景。"));
        inputs.add(createPara(2, "这是段落B，继续描述。"));

        List<SemanticSegment> segments = semanticBuilder.build(inputs);

        // Expectation: Merged into 1 segment due to high similarity > 0.85
        assertEquals(1, segments.size());
        assertEquals(2, segments.get(0).getParagraphs().size());
    }

    @Test
    void testSemanticCutWithEmbedding() {
        EmbeddingService mockEmbeddingService = new EmbeddingService() {
            @Override
            public List<float[]> embedBatch(List<String> texts) {
                List<float[]> result = new ArrayList<>();
                for (String text : texts) {
                    if (text.contains("A")) {
                        result.add(new float[]{1.0f, 0.0f, 0.0f}); 
                    } else if (text.contains("C")) {
                        result.add(new float[]{0.0f, 1.0f, 0.0f}); // Cosine similarity 0.0 < 0.65
                    } else {
                        result.add(new float[]{0.0f, 0.0f, 1.0f});
                    }
                }
                return result;
            }
        };
        ContextAwareSegmentBuilder semanticBuilder = new ContextAwareSegmentBuilder(mockEmbeddingService);

        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "这是段落A，描述场景A。"));
        inputs.add(createPara(2, "这是段落C，完全不同的场景。"));

        List<SemanticSegment> segments = semanticBuilder.build(inputs);

        // Expectation: Cut into 2 segments due to low similarity < 0.65
        assertEquals(2, segments.size());
        assertEquals(1, segments.get(0).getParagraphs().size());
        assertEquals(1, segments.get(1).getParagraphs().size());
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
