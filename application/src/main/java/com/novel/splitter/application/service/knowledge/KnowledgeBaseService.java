package com.novel.splitter.application.service.knowledge;

import com.novel.splitter.domain.model.Scene;
import java.util.List;

/**
 * 知识库管理服务接口
 */
public interface KnowledgeBaseService {
    
    /**
     * 获取指定小说的所有 Scene
     * @param novelName 小说名称
     * @return Scene 列表
     */
    List<Scene> getScenesByNovel(String novelName);

    /**
     * 根据 ID 获取 Scene
     * @param id Scene ID
     * @return Scene 对象
     */
    Scene getSceneById(String id);

    /**
     * 更新 Scene
     * @param scene 更新后的 Scene 对象
     */
    void updateScene(Scene scene);

    /**
     * 删除 Scene
     * @param id Scene ID
     */
    void deleteScene(String id);

    /**
     * 获取指定小说的所有版本列表
     * @param novelName 小说名称
     * @return 版本列表
     */
    List<String> listVersions(String novelName);
}
