package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.ChapterData;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

public interface NovelCacheRepository {
    Path rawOriginalPath(String novelId);

    Path rawDirPath(String novelId);

    Path parsedDirPath(String novelId);

    Path parsedChapterPath(String novelId, int chapterIndex);

    void saveChapter(String novelId, int chapterIndex, ChapterData chapterData);

    ChapterData loadChapter(String novelId, int chapterIndex);

    Stream<Path> listChapterFiles(String novelId);

    InputStream openChapterInputStream(String novelId, int chapterIndex);

    OutputStream openChapterOutputStream(String novelId, int chapterIndex);

    void removeParsedArtifacts(String novelId);

    void removeNovelArtifacts(String novelId);
}