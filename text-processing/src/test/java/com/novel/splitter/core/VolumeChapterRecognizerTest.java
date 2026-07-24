package com.novel.splitter.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VolumeChapterRecognizerTest {

    private final VolumeChapterRecognizer recognizer = new VolumeChapterRecognizer();

    @Test
    void testVolumeTitleDetection_colon() {
        assertTrue(recognizer.isVolumeTitleLine("卷：阿里布达年代祭 第五十三集"));
        assertTrue(recognizer.isVolumeTitleLine("卷:阿里布达年代祭 第一集"));
    }

    @Test
    void testVolumeTitleDetection_numberedVolume() {
        assertTrue(recognizer.isVolumeTitleLine("第五十三集"));
        assertTrue(recognizer.isVolumeTitleLine("第一卷"));
        assertTrue(recognizer.isVolumeTitleLine("第1卷"));
    }

    @Test
    void testVolumeTitleDetection_falsePositive() {
        // "卷" 出现在长句正文中不应被识别
        assertFalse(recognizer.isVolumeTitleLine("他展开一卷书册"));
        assertFalse(recognizer.isVolumeTitleLine("台风席卷了整个城市"));
    }

    @Test
    void testExtractVolumeName_colon() {
        assertEquals("阿里布达年代祭 第五十三集",
                recognizer.extractVolumeName("卷：阿里布达年代祭 第五十三集"));
        assertEquals("第五十三集",
                recognizer.extractVolumeName("卷:第五十三集"));
    }

    @Test
    void testExtractVolumeName_numberedVolume() {
        assertEquals("第五十三集",
                recognizer.extractVolumeName("第五十三集"));
        assertEquals("第一卷",
                recognizer.extractVolumeName("第一卷"));
    }

    @Test
    void testExtractVolumeName_withDecoratorLines() {
        assertEquals("阿里布达年代祭 第五十三集",
                recognizer.extractVolumeName("====卷：阿里布达年代祭 第五十三集===="));
    }

    @Test
    void testBuildFullChapterTitle() {
        assertEquals("第五十三集-第1章 鼎天剑气·罗汉神威",
                recognizer.buildFullChapterTitle("第五十三集", "第1章 鼎天剑气·罗汉神威"));
    }

    @Test
    void testBuildFullChapterTitle_noVolume() {
        assertEquals("第1章 鼎天剑气",
                recognizer.buildFullChapterTitle("", "第1章 鼎天剑气"));
        assertEquals("第1章 鼎天剑气",
                recognizer.buildFullChapterTitle(null, "第1章 鼎天剑气"));
    }

    @Test
    void testChapterTitleDetection() {
        assertTrue(recognizer.isChapterTitleLine("第1章 鼎天剑气·罗汉神威"));
        assertTrue(recognizer.isChapterTitleLine("第一章 南蛮绝境"));
        assertFalse(recognizer.isChapterTitleLine("第三个包子真好吃"));
    }

    @Test
    void testCustomPatterns() {
        VolumeChapterRecognizer custom = new VolumeChapterRecognizer(
                null,
                VolumeChapterRecognizer.compileUserVolumePattern("第\\d+集")
        );
        assertTrue(custom.isVolumeTitleLine("第53集"));
        assertFalse(custom.isVolumeTitleLine("卷：阿里布达年代祭"));
    }
}
