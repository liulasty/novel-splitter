package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.model.paging.PageQuery;

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
     * 保存切分好的 Scene 列表，并返回持久化后的 ID 列表
     * @param novelId 小说ID
     * @param novelName 小说名称
     * @param version 策略版本
     * @param scenes Scene 列表
     * @return 数据库中的自增主键 ID 列表
     */
    List<Long> saveScenes(String novelId, String novelName, String version, List<Scene> scenes);

    /**
     * 加载指定版本的切分结果
     * @param novelName 小说名称
     * @param version 版本
     * @return Scene 列表
     */
    List<Scene> loadScenes(String novelName, String version);

    /**
     * 按 ID 列表查询
     * @param ids ID 列表
     * @return Scene 列表
     */
    List<Scene> findByIds(List<Long> ids);

    /**
     * 按 Scene ID (String) 列表查询
     * @param sceneIds Scene ID 列表
     * @return Scene 列表
     */
    List<Scene> findBySceneIds(List<String> sceneIds);

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
     * 按小说 ID 删除指定小说的所有数据
     * @param novelId novels 表主键
     */
    void deleteNovelById(String novelId);

    /**
     * 按小说 ID 删除指定版本的切分结果
     * @param novelId novels 表主键
     * @param version 版本
     */
    void deleteVersionByNovelId(String novelId, String version);

    /**
     * 删除所有数据
     */
    void deleteAll();

    /**
     * 获取指定小说的所有版本列表
     * @param novelName 小说名称
     * @return 版本列表
     */
    List<String> listVersions(String novelName);

    /**
     * 获取指定小说（按 novelId）的所有版本列表
     * @param novelId novels 表主键
     * @return 版本列表
     */
    List<String> listVersionsByNovelId(String novelId);
    
    /**
     * 查找指定小说的所有 Scene (Convenience method, delegates to loadScenes for all versions or specific logic)
     * @param novelName 小说名称
     * @return Scene 列表
     */
    List<Scene> findByNovel(String novelName);

    /**
     * 按小说 ID 获取全部 Scene（不分页）
     * @param novelId novels 表主键
     * @return Scene 列表
     */
    List<Scene> findAllByNovelId(String novelId);

    /**
     * 统计指定小说和版本下的场景数量
     * @param novelName 小说名称
     * @param version 版本
     * @return 数量
     */
    long countByNovelNameAndVersion(String novelName, String version);

    /**
     * 分页查询轻量级场景信息，供前端列表/预览使用
     * 这里的返回类型应该是一个纯净的 DTO 或者是 Domain 模型投影，但为兼容当前项目结构先用 Object 数组或 Domain 投射
     */
    PagedResult<Scene> findLightweightScenes(PageQuery pageQuery);

    /**
     * 根据小说 ID 分页获取场景
     * @param novelId 小说 ID
     * @param pageable 分页参数
     * @return 场景分页
     */
    PagedResult<Scene> findByNovelId(String novelId, PageQuery pageQuery);

    /**
     * 根据小说 ID 和章节 ID 分页获取场景，避免一次性加载过大结果集
     */
    PagedResult<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId, PageQuery pageQuery);
    /**
     * 统计所有小说和版本下的场景数量，返回格式为 Object[] {novelName, version, count}
     * @return 统计结果列表
     */
    List<Object[]> countScenesByNovelAndVersion();
}
