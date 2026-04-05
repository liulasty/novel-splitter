package com.novel.splitter.pipeline.etl;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.infrastructure.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Slf4j
@Service
public class NovelCacheService {

    private final Path cacheDir;

    public NovelCacheService(@Value("${splitter.storage.root-path:data/novel-storage}") String storageRoot) {
        this.cacheDir = Paths.get(storageRoot, "cache", "tasks");
        try {
            Files.createDirectories(cacheDir);
        } catch (Exception e) {
            log.error("Failed to create cache directory: {}", cacheDir, e);
        }
    }

    private Path getCachePath(String taskId) {
        return cacheDir.resolve(taskId + ".json");
    }

    private Path getChapterCachePath(String taskId, int chapterIndex) {
        Path taskDir = cacheDir.resolve(taskId);
        try {
            Files.createDirectories(taskDir);
        } catch (Exception e) {
            log.error("Failed to create task cache directory: {}", taskDir, e);
        }
        return taskDir.resolve("chapter_" + chapterIndex + ".json");
    }

    public void saveChapter(String taskId, int chapterIndex, ChapterData chapterData) {
        Path path = getChapterCachePath(taskId, chapterIndex);
        try {
            JsonUtils.writeToFile(path, chapterData);
            log.debug("Saved Chapter cache for task {} chapter {} to {}", taskId, chapterIndex, path);
        } catch (Exception e) {
            log.error("Failed to save Chapter cache for task {} chapter {}", taskId, chapterIndex, e);
            throw new RuntimeException("Chapter cache save failed", e);
        }
    }

    public ChapterData loadChapter(String taskId, int chapterIndex) {
        Path path = getChapterCachePath(taskId, chapterIndex);
        if (!Files.exists(path)) {
            throw new RuntimeException("Chapter cache file not found for task " + taskId + " chapter " + chapterIndex);
        }
        try {
            return JsonUtils.readFromFile(path, ChapterData.class);
        } catch (Exception e) {
            log.error("Failed to load Chapter cache for task {} chapter {}", taskId, chapterIndex, e);
            throw new RuntimeException("Chapter cache load failed", e);
        }
    }

    public Stream<Path> listChapterFiles(String taskId) {
        Path taskDir = cacheDir.resolve(taskId);
        if (!Files.exists(taskDir)) {
            return Stream.empty();
        }
        try {
            return Files.list(taskDir).filter(p -> p.getFileName().toString().startsWith("chapter_") && p.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            log.error("Failed to list chapter files for task {}", taskId, e);
            return Stream.empty();
        }
    }

    public void save(String taskId, Novel novel) {
        Path path = getCachePath(taskId);
        try {
            JsonUtils.writeToFile(path, novel);
            log.info("Saved Novel cache for task {} to {}", taskId, path);
        } catch (Exception e) {
            log.error("Failed to save Novel cache for task {}", taskId, e);
            throw new RuntimeException("Cache save failed", e);
        }
    }

    public Novel load(String taskId) {
        Path path = getCachePath(taskId);
        if (!Files.exists(path)) {
            throw new RuntimeException("Cache file not found for task " + taskId);
        }
        try {
            Novel novel = JsonUtils.readFromFile(path, Novel.class);
            log.info("Loaded Novel cache for task {} from {}", taskId, path);
            return novel;
        } catch (Exception e) {
            log.error("Failed to load Novel cache for task {}", taskId, e);
            throw new RuntimeException("Cache load failed", e);
        }
    }

    public void remove(String taskId) {
        Path path = getCachePath(taskId);
        Path taskDir = cacheDir.resolve(taskId);
        try {
            Files.deleteIfExists(path);
            if (Files.exists(taskDir)) {
                try (Stream<Path> stream = Files.walk(taskDir)) {
                    stream.sorted(java.util.Comparator.reverseOrder())
                          .forEach(p -> {
                              try {
                                  Files.deleteIfExists(p);
                              } catch (IOException e) {
                                  log.warn("Failed to delete cache file: {}", p);
                              }
                          });
                }
            }
            log.info("Removed Novel cache for task {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to remove Novel cache for task {}", taskId, e);
        }
    }
}
