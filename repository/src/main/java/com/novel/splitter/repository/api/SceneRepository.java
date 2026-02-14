package com.novel.splitter.repository.api;

import com.novel.splitter.domain.model.Scene;
import java.util.List;

/**
 * Scene 存储仓库接口
 * <p>
 * 遵循“文件产物管理器”原则，管理切分后的文件产物。
 * 不提供细粒度的数据库式操作（如 update, delete single scene）。
 * </p>
 */
public interface SceneRepository {
    /**
     * 保存切分好的 Scene 列表（作为一个文件产物）
     * @param novelName 小说名称（作为目录名）
     * @param version 策略版本（作为子目录名）
     * @param scenes Scene 列表
     */
    void saveScenes(String novelName, String version, List<Scene> scenes);

    /**
     * 加载指定版本的切分结果
     * @param novelName 小说名称
     * @param version 版本
     * @return Scene 列表
     */
    List<Scene> loadScenes(String novelName, String version);

    /**
     * 删除指定小说的指定版本（删除文件产物）
     * @param novelName 小说名称
     * @param version 版本
     */
    void deleteVersion(String novelName, String version);

    /**
     * 删除指定小说的所有数据
     * @param novelName 小说名称
     */
    void deleteNovel(String novelName);

    /**
     * 获取指定小说的所有版本列表
     * @param novelName 小说名称
     * @return 版本列表
     */
    List<String> listVersions(String novelName);
    
    /**
     * 查找指定小说的所有 Scene (Convenience method, delegates to loadScenes for all versions or specific logic)
     * @param novelName 小说名称
     * @return Scene 列表
     */
    List<Scene> findByNovel(String novelName);
}
