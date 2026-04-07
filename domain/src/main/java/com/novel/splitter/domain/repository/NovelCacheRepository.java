package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.model.Novel;

import java.nio.file.Path;
import java.util.stream.Stream;

public interface NovelCacheRepository {
    void saveChapter(String taskId, int chapterIndex, ChapterData chapterData);
    ChapterData loadChapter(String taskId, int chapterIndex);
    Stream<Path> listChapterFiles(String taskId);
    void save(String taskId, Novel novel);
    Novel load(String taskId);
    void remove(String taskId);
}