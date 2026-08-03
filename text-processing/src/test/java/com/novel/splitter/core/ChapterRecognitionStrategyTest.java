package com.novel.splitter.core;

import com.novel.splitter.domain.enums.RecognitionStrategyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterRecognitionStrategyTest {

    @Test
    void cnChapterMatchesChineseChapterFormat() {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.forType(RecognitionStrategyType.CN_CHAPTER, null);
        assertTrue(s.matches("第一章 初入江湖"));
        assertTrue(s.matches("第12章"));
        assertFalse(s.matches("第12回"));
        assertFalse(s.matches("Chapter 3"));
    }

    @Test
    void cnBackMatchesChineseBackFormat() {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.forType(RecognitionStrategyType.CN_BACK, null);
        assertTrue(s.matches("第3回 风云再起"));
        assertFalse(s.matches("第3章"));
    }

    @Test
    void enChapterMatchesEnglishChapterFormat() {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.forType(RecognitionStrategyType.EN_CHAPTER, null);
        assertTrue(s.matches("Chapter 12"));
        assertTrue(s.matches("chapter 3"));
        assertFalse(s.matches("第12章"));
    }

    @Test
    void prologueMatchesOpeningFormats() {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.forType(RecognitionStrategyType.PROLOGUE, null);
        assertTrue(s.matches("序章"));
        assertTrue(s.matches("楔子"));
        assertFalse(s.matches("第1章"));
    }

    @Test
    void customUsesProvidedRegex() {
        ChapterRecognitionStrategy s = ChapterRecognitionStrategy.custom("^第.+話.*$");
        assertTrue(s.matches("第1話 起始"));
        assertFalse(s.matches("第1章 起始"));
    }
}
