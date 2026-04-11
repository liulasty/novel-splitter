package com.novel.splitter.domain.strategy;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverlapChunkingStrategyTest {

    @Test
    void childChunksInheritParentMetadataAndFirstChildPrefix() {
        SceneMetadata parentMeta = SceneMetadata.builder()
                .novel("novel-1")
                .version("v1")
                .chapterTitle("第一章 试炼")
                .chapterIndex(1)
                .startParagraph(0)
                .endParagraph(50)
                .role(null)
                .densityScore(0.42)
                .qualityScore(0.9)
                .extra(Map.of("k", "v"))
                .build();

        // 前 200 字与后段区分，便于断言重叠区仅出现在 prefix_context
        String parentBody = "a".repeat(200) + "b".repeat(400);
        Scene parent = Scene.builder()
                .id("parent-uuid")
                .chapterTitle("第一章 试炼")
                .chapterIndex(1)
                .startParagraphIndex(0)
                .endParagraphIndex(50)
                .text(parentBody)
                .wordCount(600)
                .prefixContext("…上文结尾")
                .canSplit(true)
                .metadata(parentMeta)
                .build();

        OverlapChunkingStrategy strategy = new OverlapChunkingStrategy(200, 40);
        List<Scene> children = strategy.split(parent);

        assertTrue(children.size() >= 2);
        Scene first = children.get(0);
        assertEquals("…上文结尾", first.getPrefixContext());
        assertEquals(0, first.getMetadata().getSequenceNum());
        assertEquals("novel-1", first.getMetadata().getNovel());
        assertEquals("第一章 试炼", first.getMetadata().getChapterTitle());
        assertEquals(1, first.getMetadata().getChapterIndex());
        assertEquals(0, first.getMetadata().getStartParagraph());
        assertEquals(50, first.getMetadata().getEndParagraph());
        assertEquals(0.42, first.getMetadata().getDensityScore());
        assertEquals(0.9, first.getMetadata().getQualityScore());
        assertNull(first.getMetadata().getRole());
        assertEquals("v", first.getMetadata().getExtra().get("k"));

        Scene second = children.get(1);
        // 滑窗重叠区写入 prefix_context，正文从下一块新内容起算（此处为 b 段）
        assertEquals("a".repeat(40), second.getPrefixContext());
        assertTrue(second.getText().startsWith("b"));
        assertEquals(1, second.getMetadata().getSequenceNum());
        assertEquals(1, second.getMetadata().getChapterIndex());
    }

    @Test
    void singleChildWhenTextFitsChunkSize() {
        Scene parent = Scene.builder()
                .id("p2")
                .chapterIndex(2)
                .chapterTitle("第二章")
                .startParagraphIndex(10)
                .endParagraphIndex(12)
                .text("short")
                .wordCount(5)
                .prefixContext(null)
                .canSplit(false)
                .metadata(SceneMetadata.builder()
                        .novel("n")
                        .chapterIndex(2)
                        .startParagraph(10)
                        .endParagraph(12)
                        .build())
                .build();

        List<Scene> children = new OverlapChunkingStrategy(500, 50).split(parent);
        assertEquals(1, children.size());
        assertFalse(children.get(0).isCanSplit());
        assertEquals(2, children.get(0).getMetadata().getChapterIndex());
    }
}
