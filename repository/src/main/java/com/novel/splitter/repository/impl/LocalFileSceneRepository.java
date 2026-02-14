package com.novel.splitter.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.infrastructure.json.JsonUtils;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 本地文件系统实现的 Scene 仓库
 * <p>
 * 存储结构：root/scene/{novelName}/{version}/scenes.json
 * 作为“文件产物管理器”，只负责文件的存取和管理，不维护细粒度索引。
 * </p>
 */
@Slf4j
public class LocalFileSceneRepository implements SceneRepository {

    private final Path storageRoot;

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
            log.info("Saved {} scenes to {}", scenes.size(), file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save scenes to " + dir, e);
        }
    }

    @Override
    public List<Scene> loadScenes(String novelName, String version) {
        Path file = storageRoot.resolve("scene").resolve(novelName).resolve(version).resolve("scenes.json");
        if (!Files.exists(file)) {
            log.warn("Scenes file not found: {}", file);
            return new ArrayList<>();
        }
        try {
            Scene[] sceneArray = JsonUtils.readFromFile(file, Scene[].class);
            return sceneArray != null ? new ArrayList<>(Arrays.asList(sceneArray)) : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to load scenes from " + file, e);
            throw new RuntimeException("Failed to load scenes from " + file, e);
        }
    }

    @Override
    public List<Scene> findByNovel(String novelName) {
        List<Scene> allScenes = new ArrayList<>();
        List<String> versions = listVersions(novelName);
        for (String version : versions) {
            allScenes.addAll(loadScenes(novelName, version));
        }
        return allScenes;
    }

    @Override
    public void deleteVersion(String novelName, String version) {
        log.info("Deleting version: {}/{}", novelName, version);
        Path dir = storageRoot.resolve("scene").resolve(novelName).resolve(version);
        deleteDirectory(dir);
    }

    @Override
    public void deleteNovel(String novelName) {
        log.info("Deleting novel: {}", novelName);
        Path dir = storageRoot.resolve("scene").resolve(novelName);
        deleteDirectory(dir);
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

    private void deleteDirectory(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            log.error("Failed to delete directory: " + path, e);
            throw new RuntimeException("Failed to delete directory: " + path, e);
        }
    }
}
