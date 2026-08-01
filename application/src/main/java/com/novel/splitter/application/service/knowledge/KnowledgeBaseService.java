package com.novel.splitter.application.service.knowledge;

import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.SceneSplitProfileDto;
import com.novel.splitter.application.model.dto.VectorPreviewRecordDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 知识库管理服务接口
 */
public interface KnowledgeBaseService {
    
    /**
     * 获取轻量级场景分页列表
     * @param pageable 分页参数
     * @return 轻量级场景分页列表
     */
    Page<VectorPreviewRecordDto> getLightweightScenes(Pageable pageable);

    /**
     * 获取指定小说的所有 Scene
     * @param novelName 小说名称
     * @param version 版本过滤（可选，为空时返回全部分区场景）
     * @return Scene 列表
     */
    List<SceneDto> getScenesByNovel(String novelName, String version);

    /**
     * 按 novelId 获取指定小说的全部 Scene
     * @param novelId novels 表主键
     * @param version 版本过滤（可选，为空时返回全部分区场景）
     * @return Scene 列表
     */
    List<SceneDto> getScenesByNovelId(String novelId, String version);

    /**
     * 删除指定 (version, chunkSize, chunkOverlap) 分区下的切分结果（及相关向量）
     *
     * @param purgeTerminalSplitTasks 为 true 时，在已通过「无进行中任务」校验的前提下，额外删除本书
     *                                {@code SUCCESS}/{@code FAILED} 的 {@code split_tasks}（及 task_events）；
     *                                仅按 {@code version} 字符串匹配任务行，chunk 参数不在任务表上。
     */
    Long deleteVersion(String novelName, String version, int chunkSize, int chunkOverlap, boolean purgeTerminalSplitTasks);

    /**
     * 删除指定小说的所有数据（文件、切分结果、向量）
     * @param novelName 小说名称
     * @param purgeTerminalSplitTasks 为 true 时删除本书终态 split_tasks / task_events，见 {@link #deleteVersion}
     * @return cleanupTaskId
     */
    Long deleteKnowledgeBase(String novelName, boolean purgeTerminalSplitTasks);

    /**
     * 按 novelId 删除指定小说的所有数据（文件、切分结果、向量）
     * @param novelId novels 表主键
     * @param purgeTerminalSplitTasks 为 true 时删除本书终态 split_tasks / task_events，见 {@link #deleteVersion}
     * @return cleanupTaskId
     */
    Long deleteKnowledgeBaseById(String novelId, boolean purgeTerminalSplitTasks);

    /**
     * 获取指定小说的所有版本列表
     * @param novelName 小说名称
     * @return 版本列表
     */
    List<String> listVersions(String novelName);

    /**
     * 获取指定小说（按 novelId）的展示标签列表（含滑窗参数），兼容旧前端。
     */
    List<String> listVersionsByNovelId(String novelId);

    /**
     * 按 novelId 返回结构化切分数据集列表（version + chunk 参数）。
     */
    List<SceneSplitProfileDto> listSplitProfilesByNovelId(String novelId);

    /**
     * 按 novelId 删除指定 (version, chunkSize, chunkOverlap) 分区下的场景与对应向量。
     *
     * @param purgeTerminalSplitTasks 见 {@link #deleteVersion}
     */
    Long deleteSplitProfileByNovelId(String novelId, String version, int chunkSize, int chunkOverlap, boolean purgeTerminalSplitTasks);
}
