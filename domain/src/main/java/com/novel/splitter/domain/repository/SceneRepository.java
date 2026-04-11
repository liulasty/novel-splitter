package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.model.paging.PageQuery;

import java.util.List;

/**
 * Scene 存储仓库接口
 */
public interface SceneRepository {
    /**
     * 保存切分好的 Scene；分区键为 (novelId, version, chunkSize, chunkOverlap)。
     */
    List<Long> saveScenes(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes);

    List<Scene> findByIds(List<Long> ids);

    List<Scene> findBySceneIds(List<String> sceneIds);

    void deleteNovelById(String novelId);

    /**
     * 幂等清理：软删指定业务版本 + 滑窗参数下的全部场景。
     */
    void deleteByProfile(String novelId, String version, int chunkSize, int chunkOverlap);

    void deleteAll();

    /**
     * 某小说下已存在的场景数据集（去重）。
     */
    List<SceneSplitProfile> listSplitProfilesByNovelId(String novelId);

    List<Scene> findAllByNovelId(String novelId);

    /** 同一 business version 下所有 chunk 分区（含 legacy null chunk 列） */
    List<Scene> findAllByNovelIdAndVersion(String novelId, String version);

    List<Scene> findByProfile(String novelId, String version, int chunkSize, int chunkOverlap);

    long countByProfile(String novelId, String version, int chunkSize, int chunkOverlap);

    /** 同一 business version 下所有 chunk 分区的场景总数 */
    long countAllByNovelIdAndVersion(String novelId, String version);

    PagedResult<Scene> findLightweightScenes(PageQuery pageQuery);

    PagedResult<Scene> findByNovelId(String novelId, PageQuery pageQuery);

    PagedResult<Scene> findByProfile(String novelId, String version, int chunkSize, int chunkOverlap, PageQuery pageQuery);

    PagedResult<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId, PageQuery pageQuery);

    /**
     * 统计：按 novelId、version、chunk 分组后的场景数量。
     */
    List<SceneCountByProfile> countScenesByNovelVersionAndChunk();
}
