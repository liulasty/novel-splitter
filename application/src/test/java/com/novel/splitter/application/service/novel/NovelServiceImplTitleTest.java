package com.novel.splitter.application.service.novel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NovelServiceImplTitleTest {

    @Test
    void explicitTitleWins() {
        assertEquals("手动标题", NovelServiceImpl.resolveNovelTitle("手动标题", "a.txt", "x/y/z.txt"));
    }

    @Test
    void usesUploadOriginalFilenameBasename() {
        assertEquals("我的书", NovelServiceImpl.resolveNovelTitle(null, "我的书.txt", "novel-raw/uuid/original.txt"));
    }

    @Test
    void fallsBackToLastPathSegmentOfStoredPath() {
        assertEquals("original", NovelServiceImpl.resolveNovelTitle(null, null, "novel-raw/uuid/original.txt"));
    }

    @Test
    void untitledWhenNothingUsable() {
        assertEquals("未命名小说", NovelServiceImpl.resolveNovelTitle(null, null, ""));
    }
}
