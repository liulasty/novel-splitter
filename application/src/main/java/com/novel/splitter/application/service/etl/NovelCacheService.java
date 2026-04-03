package com.novel.splitter.application.service.etl;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.infrastructure.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        try {
            Files.deleteIfExists(path);
            log.info("Removed Novel cache for task {}", taskId);
        } catch (Exception e) {
            log.warn("Failed to remove Novel cache for task {}", taskId, e);
        }
    }
}
