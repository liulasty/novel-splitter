package com.novel.splitter.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.infrastructure.json.JsonUtils;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    // 注意：在分布式或多实例环境下，这种缓存是不可靠的。但对于本地单机工具足够了。
    // 如果数据量巨大，需要换成数据库或按需读取。
    private final Map<String, Scene> cache = new ConcurrentHashMap<>();

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

        // 2. 如果缓存未命中（例如服务重启），尝试扫描磁盘并加载
        // 警告：这是一个非常重的操作，实际生产环境应该使用数据库
        log.warn("Scene cache miss for id: {}. Scanning disk...", id);
        scanAndLoadCache();
        
        return Optional.ofNullable(cache.get(id));
    }

    private synchronized void scanAndLoadCache() {
        // 防止并发重复扫描
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
        }
    }

    private void loadScenesFromFile(Path path) {
        try {
            // 使用数组方式读取，绕过泛型擦除问题
            Scene[] sceneArray = JsonUtils.readFromFile(path, Scene[].class);
            if (sceneArray != null) {
                for (Scene scene : sceneArray) {
                    cache.put(scene.getId(), scene);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load scenes from file: " + path, e);
        }
    }
}
