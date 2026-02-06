package com.novel.splitter.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.infrastructure.json.JsonUtils;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地文件系统实现的 Scene 仓库
 * <p>
 * 存储结构：root/scene/{novelName}/{version}/scenes.json
 * </p>
 */
@Slf4j
public class LocalFileSceneRepository implements SceneRepository {

    private final Path storageRoot;
    
    // 简单的内存缓存：SceneID -> Scene
    private final Map<String, Scene> cache = new ConcurrentHashMap<>();
    // 简单的文件映射：SceneID -> FilePath
    private final Map<String, Path> sceneFileMap = new ConcurrentHashMap<>();

    public LocalFileSceneRepository(String storageRootPath) {
        this.storageRoot = Paths.get(storageRootPath);
    }

    @Override
    public void saveScenes(String novelName, String version, List<Scene> scenes) {
        Path dir = storageRoot.resolve("scene").resolve(novelName).resolve(version);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            Path file = dir.resolve("scenes.json");
            JsonUtils.writeToFile(file, scenes);
            
            // 更新缓存
            for (Scene scene : scenes) {
                cache.put(scene.getId(), scene);
                sceneFileMap.put(scene.getId(), file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save scenes to " + dir, e);
        }
    }

    @Override
    public Optional<Scene> findById(String id) {
        // 1. 查缓存
        if (cache.containsKey(id)) {
            return Optional.of(cache.get(id));
        }

        // 2. 如果缓存未命中，尝试扫描磁盘
        log.warn("Scene cache miss for id: {}. Scanning disk...", id);
        scanAndLoadCache();
        
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<Scene> findByNovel(String novelName) {
        try {
            scanAndLoadCache(); // 确保缓存已加载
            return cache.values().stream()
                    .filter(s -> s.getMetadata() != null && novelName.equals(s.getMetadata().getNovel()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error finding scenes for novel: " + novelName, e);
            throw new RuntimeException("Error finding scenes for novel: " + novelName, e);
        }
    }

    @Override
    public synchronized void update(Scene scene) {
        try {
            if (!cache.containsKey(scene.getId())) {
                 // 尝试加载
                 scanAndLoadCache();
                 if (!cache.containsKey(scene.getId())) {
                     throw new RuntimeException("Scene not found: " + scene.getId());
                 }
            }
    
            Path file = sceneFileMap.get(scene.getId());
            if (file == null) {
                 throw new RuntimeException("Scene file not found for: " + scene.getId());
            }
    
            // 这是一个低效实现：读取文件，替换，写回
            Scene[] sceneArray = JsonUtils.readFromFile(file, Scene[].class);
            List<Scene> scenes = new ArrayList<>(Arrays.asList(sceneArray));
            
            boolean found = false;
            for (int i = 0; i < scenes.size(); i++) {
                if (scenes.get(i).getId().equals(scene.getId())) {
                    scenes.set(i, scene);
                    found = true;
                    break;
                }
            }
            
            if (found) {
                JsonUtils.writeToFile(file, scenes);
                cache.put(scene.getId(), scene);
            }
        } catch (Exception e) {
            log.error("Error updating scene: " + scene.getId(), e);
            throw new RuntimeException("Error updating scene: " + scene.getId(), e);
        }
    }

    @Override
    public synchronized void delete(String id) {
        try {
            if (!cache.containsKey(id)) {
                 scanAndLoadCache();
                 if (!cache.containsKey(id)) {
                     return; // Already deleted or not exist
                 }
            }
    
            Path file = sceneFileMap.get(id);
            if (file == null) {
                 throw new RuntimeException("Scene file not found for: " + id);
            }
    
            Scene[] sceneArray = JsonUtils.readFromFile(file, Scene[].class);
            List<Scene> scenes = new ArrayList<>(Arrays.asList(sceneArray));
            
            boolean removed = scenes.removeIf(s -> s.getId().equals(id));
            
            if (removed) {
                JsonUtils.writeToFile(file, scenes);
                cache.remove(id);
                sceneFileMap.remove(id);
            }
        } catch (Exception e) {
            log.error("Error deleting scene: " + id, e);
            throw new RuntimeException("Error deleting scene: " + id, e);
        }
    }

    @Override
    public List<String> listVersions(String novelName) {
        Path novelDir = storageRoot.resolve("scene").resolve(novelName);
        if (!Files.exists(novelDir) || !Files.isDirectory(novelDir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(novelDir)) {
            return stream.filter(Files::isDirectory)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to list versions for novel: " + novelName, e);
            return new ArrayList<>();
        }
    }

    private synchronized void scanAndLoadCache() {
        if (!cache.isEmpty()) {
            return;
        }
        
        Path sceneRoot = storageRoot.resolve("scene");
        if (!Files.exists(sceneRoot)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(sceneRoot)) {
            walk.filter(p -> p.toString().endsWith("scenes.json"))
                .forEach(this::loadScenesFromFile);
        } catch (IOException e) {
            log.error("Failed to scan scene directory", e);
            // We don't throw here to allow partial functionality
        } catch (Exception e) {
            log.error("Unexpected error during cache scan", e);
        }
    }

    private void loadScenesFromFile(Path path) {
        try {
            Scene[] sceneArray = JsonUtils.readFromFile(path, Scene[].class);
            if (sceneArray != null) {
                for (Scene scene : sceneArray) {
                    cache.put(scene.getId(), scene);
                    sceneFileMap.put(scene.getId(), path);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load scenes from file: " + path, e);
        }
    }
}
