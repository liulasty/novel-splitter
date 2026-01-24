package com.novel.splitter.core;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.RawParagraph;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChapterRecognizerTest {

    private final ChapterRecognizer recognizer = new ChapterRecognizer();

    @Test
    void testRecognizeSimpleChapters() {
        List<String> lines = Arrays.asList(
                "前言",
                "这是前言内容",
                "第1章 冒险开始",
                "第一段内容",
                "第二段内容",
                "第2章 遭遇战",
                "第三段内容"
        );
        List<RawParagraph> paragraphs = toParagraphs(lines);

        List<Chapter> chapters = recognizer.recognize(paragraphs);

        assertEquals(3, chapters.size());
        
        // Check Chapter 0 (Preface)
        assertEquals("序章/前言", chapters.get(0).getTitle());
        assertEquals(0, chapters.get(0).getStartParagraphIndex());
        assertEquals(1, chapters.get(0).getEndParagraphIndex());

        // Check Chapter 1
        assertEquals("第1章 冒险开始", chapters.get(1).getTitle());
        assertEquals(2, chapters.get(1).getStartParagraphIndex());
        assertEquals(4, chapters.get(1).getEndParagraphIndex());

        // Check Chapter 2
        assertEquals("第2章 遭遇战", chapters.get(2).getTitle());
        assertEquals(5, chapters.get(2).getStartParagraphIndex());
        assertEquals(6, chapters.get(2).getEndParagraphIndex());
    }

    @Test
    void testChineseNumerals() {
        List<String> lines = Arrays.asList(
                "第一章", "内容1",
                "第二十回", "内容2",
                "第三百零五节", "内容3"
        );
        List<RawParagraph> paragraphs = toParagraphs(lines);
        List<Chapter> chapters = recognizer.recognize(paragraphs);

        // Note: First chapter includes lines before the first match if any.
        // Here "第一章" is at index 0.
        // Logic: if currentStart (0) < i (0), add previous chapter. 
        // So loop i=0 matches. i > currentStart is false.
        // Update currentStart=0.
        // Loop i=2 matches. i > currentStart (2 > 0). Add chapter "第一章". Range 0-1.
        
        assertEquals(3, chapters.size());
        assertEquals("第一章", chapters.get(0).getTitle());
        assertEquals("第二十回", chapters.get(1).getTitle());
        assertEquals("第三百零五节", chapters.get(2).getTitle());
    }
    
    @Test
    void testEnglishChapters() {
        List<String> lines = Arrays.asList(
            "Chapter 1 Start", "content",
            "Chapter 20 End", "content"
        );
        List<RawParagraph> paragraphs = toParagraphs(lines);
        List<Chapter> chapters = recognizer.recognize(paragraphs);
        
        assertEquals(2, chapters.size());
        assertEquals("Chapter 1 Start", chapters.get(0).getTitle());
    }

    @Test
    void testFalsePositives() {
        List<String> lines = Arrays.asList(
                "第一章", "内容",
                "他说道：第三个包子真好吃。", // 不应被识别
                "第5章", "内容"
        );
        List<RawParagraph> paragraphs = toParagraphs(lines);
        List<Chapter> chapters = recognizer.recognize(paragraphs);

        assertEquals(2, chapters.size());
        assertEquals("第一章", chapters.get(0).getTitle());
        assertEquals("第5章", chapters.get(1).getTitle());
        // 中间的句子应该归属到第一章
        assertEquals(0, chapters.get(0).getStartParagraphIndex());
        assertEquals(2, chapters.get(0).getEndParagraphIndex());
    }

    private List<RawParagraph> toParagraphs(List<String> lines) {
        ParagraphSplitter splitter = new ParagraphSplitter();
        return splitter.split(lines);
    }
}
