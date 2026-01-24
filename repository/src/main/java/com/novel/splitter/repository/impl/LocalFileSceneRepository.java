package com.novel.splitter.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.infrastructure.json.JsonUtils;
import com.novel.splitter.repository.api.SceneRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 本地文件系统实现的 Scene 仓库
 * <p>
 * 存储结构：root/scene/{novelName}/{version}/scenes.json
 * </p>
 */
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
        } catch (IOException e) {
            throw new RuntimeException("Failed to save scenes to " + dir, e);
        }
    }
}
