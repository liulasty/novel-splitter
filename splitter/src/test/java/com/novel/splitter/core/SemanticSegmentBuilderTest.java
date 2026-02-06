package com.novel.splitter.core;

import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.SemanticSegment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticSegmentBuilderTest {

    private final SemanticSegmentBuilder builder = new SemanticSegmentBuilder();

    @Test
    void testDialogueMerge() {
        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "“你好。”"));
        inputs.add(createPara(2, "“你好啊。”"));
        inputs.add(createPara(3, "“吃饭了吗？”"));
        inputs.add(createPara(4, "“没吃呢。”"));
        inputs.add(createPara(5, "他转身离开了。")); // Narration

        List<SemanticSegment> segments = builder.build(inputs);

        // Expectation:
        // Segment 1: Dialogue (4 paragraphs)
        // Segment 2: Narration (1 paragraph)
        assertEquals(2, segments.size());
        
        SemanticSegment s1 = segments.get(0);
        assertEquals(SemanticSegmentBuilder.TYPE_DIALOGUE, s1.getType());
        assertEquals(4, s1.getParagraphs().size());
        
        SemanticSegment s2 = segments.get(1);
        assertEquals(SemanticSegmentBuilder.TYPE_NARRATION, s2.getType());
        assertEquals(1, s2.getParagraphs().size());
    }

    @Test
    void testMixedContent() {
        List<RawParagraph> inputs = new ArrayList<>();
        inputs.add(createPara(1, "天空很蓝。"));
        inputs.add(createPara(2, "白云很白。"));
        inputs.add(createPara(3, "“快看！”"));
        inputs.add(createPara(4, "“看到了。”"));

        List<SemanticSegment> segments = builder.build(inputs);

        // Segment 1: Narration (2 paragraphs)
        // Segment 2: Dialogue (2 paragraphs)
        assertEquals(2, segments.size());
        assertEquals(SemanticSegmentBuilder.TYPE_NARRATION, segments.get(0).getType());
        assertEquals(2, segments.get(0).getParagraphs().size());
        
        assertEquals(SemanticSegmentBuilder.TYPE_DIALOGUE, segments.get(1).getType());
        assertEquals(2, segments.get(1).getParagraphs().size());
    }

    private RawParagraph createPara(int index, String content) {
        return RawParagraph.builder()
                .index(index)
                .content(content)
                .isEmpty(false)
                .build();
    }
}
