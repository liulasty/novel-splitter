package com.novel.splitter.repository.api;

import com.novel.splitter.domain.model.Scene;
import java.util.List;
import java.util.Optional;

/**
 * Scene 存储仓库接口
 */
public interface SceneRepository {
    /**
     * 保存切分好的 Scene 列表
     * @param novelName 小说名称（作为目录名）
     * @param version 策略版本（作为子目录名）
     * @param scenes Scene 列表
     */
    void saveScenes(String novelName, String version, List<Scene> scenes);

    /**
     * 根据 ID 查找 Scene
     * <p>
     * RAG 检索回溯时使用。
     * </p>
     * @param id Scene ID
     * @return Scene 对象
     */
    Optional<Scene> findById(String id);
}
