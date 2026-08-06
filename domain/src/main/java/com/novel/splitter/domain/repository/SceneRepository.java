package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.model.paging.PageQuery;

import java.util.List;
import java.util.Optional;

/**
 * Scene 存储仓库接口
 */
public interface SceneRepository {
    /**
     * 保存切分好的 Scene；分区键为 (novelId, version, chunkSize, chunkOverlap)。
     */
    List<Long> saveScenes(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes);

    /**
     * 幂等保存：以 (novelId, version, seq) 唯一约束为界，已存在则跳过；返回实际写入的 persistenceId。
     */
    List<Long> saveScenesIdempotent(String novelId, String version, int chunkSize, int chunkOverlap, List<Scene> scenes);

    /**
     * 当前已存在的最大 seq；无数据返回 0（下一批从 1 起）。
     */
    long maxSeqByVersion(String novelId, String version);

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

    List<Long> listPersistenceIdsByProfile(String novelId, String version, int chunkSize, int chunkOverlap);

    long countByProfile(String novelId, String version, int chunkSize, int chunkOverlap);

    /** 同一 business version 下所有 chunk 分区的场景总数 */
    long countAllByNovelIdAndVersion(String novelId, String version);

    PagedResult<Scene> findLightweightScenes(PageQuery pageQuery);

    PagedResult<Scene> findByNovelId(String novelId, PageQuery pageQuery);

    PagedResult<Scene> findByProfile(String novelId, String version, int chunkSize, int chunkOverlap, PageQuery pageQuery);

    PagedResult<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId, PageQuery pageQuery);

    PagedResult<Scene> findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, PageQuery pageQuery);

    /**
     * 统计：按 novelId、version、chunk 分组后的场景数量。
     */
    List<SceneCountByProfile> countScenesByNovelVersionAndChunk();

    /**
     * 按 (novelId, version, chunk 分区) 的 seq 范围查询场景，用于相邻块扩展。
     */
    List<Scene> findByProfileAndSeqRange(String novelId, String version, int chunkSize, int chunkOverlap,
                                         long fromSeq, long toSeq);

    /**
     * 批量更新场景元数据（语义抽取结果写回 metadata_json）。
     */
    void updateScenesMetadata(List<Scene> scenes);

    /**
     * 新一轮向量化前：将 profile 内场景标为待嵌入并绑定 run id。
     */
    int resetEmbedStateForRun(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId);

    void updateEmbedOutcome(Long persistenceId, String embedRunId, EmbedStatus status, String embedError);

    /**
     * 批量更新嵌入结果；{@code persistenceIds} 为空则 no-op。
     * SUCCESS 时 {@code embedError} 应为 null；FAILED 时与单条 {@link #updateEmbedOutcome} 一致须带非空错误说明。
     */
    void batchUpdateEmbedOutcome(List<Long> persistenceIds, String embedRunId, EmbedStatus status, String embedError);

    long countEmbedByRunAndStatus(String novelId, String version, int chunkSize, int chunkOverlap,
                                  String embedRunId, EmbedStatus status);

    List<Long> listPersistenceIdsForEmbedResume(String novelId, String version, int chunkSize, int chunkOverlap,
                                                String embedRunId);

    /**
     * 从参与某次向量化运行的任意场景行推断 (chunkSize, chunkOverlap)（用于任务行缺少 chunk 列时的续传）。
     */
    Optional<int[]> resolveChunkProfileForEmbedRun(String novelId, String version, String embedRunId);
}
