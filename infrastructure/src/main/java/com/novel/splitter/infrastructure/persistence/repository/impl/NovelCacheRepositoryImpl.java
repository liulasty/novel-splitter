package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.infrastructure.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Slf4j
@Service
public class NovelCacheRepositoryImpl implements NovelCacheRepository {

    private final Path rootDir;
    private final String rawDirName;
    private final String parsedDirName;
    private final String rawFilename;

    public NovelCacheRepositoryImpl(
            @Value("${splitter.storage.root-path:data/novel-storage}") String storageRoot,
            @Value("${splitter.storage.raw-dir-name:novel-raw}") String rawDirName,
            @Value("${splitter.storage.parsed-dir-name:novel-parsed}") String parsedDirName,
            @Value("${splitter.storage.raw-filename:original.txt}") String rawFilename
    ) {
        this.rootDir = Paths.get(storageRoot);
        this.rawDirName = rawDirName;
        this.parsedDirName = parsedDirName;
        this.rawFilename = rawFilename;
    }

    @Override
    public Path rawOriginalPath(String novelId) {
        return rootDir.resolve(rawDirName).resolve(novelId).resolve(rawFilename);
    }

    @Override
    public Path rawDirPath(String novelId) {
        return rootDir.resolve(rawDirName).resolve(novelId);
    }

    @Override
    public Path parsedDirPath(String novelId) {
        return rootDir.resolve(parsedDirName).resolve(novelId);
    }

    @Override
    public Path parsedChapterPath(String novelId, int chapterIndex) {
        Path novelDir = parsedDirPath(novelId);
        try {
            Files.createDirectories(novelDir);
        } catch (Exception e) {
            log.error("创建解析目录失败: {}", novelDir, e);
        }
        return novelDir.resolve("chapter_" + chapterIndex + ".json");
    }

    @Override
    public void saveChapter(String novelId, int chapterIndex, ChapterData chapterData) {
        Path path = parsedChapterPath(novelId, chapterIndex);
        try {
            JsonUtils.writeToFile(path, chapterData);
            log.debug("已保存章节缓存, novelId={} chapter={} 至 {}", novelId, chapterIndex, path);
        } catch (Exception e) {
            log.error("保存章节缓存失败, novelId={} chapter={}", novelId, chapterIndex, e);
            throw new RuntimeException("Chapter cache save failed", e);
        }
    }

    @Override
    public ChapterData loadChapter(String novelId, int chapterIndex) {
        Path path = parsedChapterPath(novelId, chapterIndex);
        if (!Files.exists(path)) {
            throw new RuntimeException("Chapter cache file not found for novelId " + novelId + " chapter " + chapterIndex);
        }
        try {
            return JsonUtils.readFromFile(path, ChapterData.class);
        } catch (Exception e) {
            log.error("加载章节缓存失败, novelId={} chapter={}", novelId, chapterIndex, e);
            throw new RuntimeException("Chapter cache load failed", e);
        }
    }

    @Override
    public Stream<Path> listChapterFiles(String novelId) {
        Path novelDir = parsedDirPath(novelId);
        if (!Files.exists(novelDir)) {
            return Stream.empty();
        }
        try {
            return Files.list(novelDir).filter(p -> p.getFileName().toString().startsWith("chapter_") && p.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            log.error("列出章节文件失败, novelId={}", novelId, e);
            return Stream.empty();
        }
    }

    @Override
    public InputStream openChapterInputStream(String novelId, int chapterIndex) {
        Path path = parsedChapterPath(novelId, chapterIndex);
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open chapter input stream: " + path, e);
        }
    }

    @Override
    public OutputStream openChapterOutputStream(String novelId, int chapterIndex) {
        Path path = parsedChapterPath(novelId, chapterIndex);
        try {
            return Files.newOutputStream(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open chapter output stream: " + path, e);
        }
    }

    @Override
    public void removeNovelArtifacts(String novelId) {
        Path rawDir = rootDir.resolve(rawDirName).resolve(novelId);
        Path parsedDir = rootDir.resolve(parsedDirName).resolve(novelId);
        try {
            deleteDirectoryRecursivelyIfExists(rawDir);
            deleteDirectoryRecursivelyIfExists(parsedDir);
            log.info("已移除小说产物, novelId={}", novelId);
        } catch (Exception e) {
            log.warn("移除小说产物失败, novelId={}", novelId, e);
        }
    }

    @Override
    public void removeParsedArtifacts(String novelId) {
        Path parsedDir = parsedDirPath(novelId);
        try {
            deleteDirectoryRecursivelyIfExists(parsedDir);
            log.info("已移除解析产物, novelId={}", novelId);
        } catch (Exception e) {
            log.warn("移除解析产物失败, novelId={}", novelId, e);
        }
    }

    private void deleteDirectoryRecursivelyIfExists(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除文件失败: {}", p);
                }
            });
        }
    }
}
