package com.novel.splitter.application.orchestration;

import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向量化编排中「Chroma 删除之后」的数据库步骤：单事务提交，避免场景重置与任务行更新之间出现部分成功。
 * <p>
 * 与向量库之间无法做真正 2PC；若本事务提交前 Chroma 删除已成功而此处失败，需依赖消息重投或人工重新触发向量化（删除为幂等）。
 * </p>
 * <p>
 * 本事务同时推进 {@link NovelVersion} 状态：startEmbed() + 写入 embedRunId / embedCursorSceneSeq=0，
 * 使向量化与版本状态机联动（全部完成后由 {@code EmbedWorker} 置 EMBED_DONE）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EmbedRunDbCoordinator {

    private final SceneRepository sceneRepository;
    private final TaskService taskService;
    private final NovelService novelService;
    private final NovelVersionRepository novelVersionRepository;

    @Transactional(rollbackFor = Exception.class)
    public int beginRunAfterVectorsCleaned(
            String taskId,
            String novelId,
            String version,
            int chunkSize,
            int chunkOverlap,
            String embedRunId) {
        sceneRepository.resetEmbedStateForRun(novelId, version, chunkSize, chunkOverlap, embedRunId);
        long total = sceneRepository.countByProfile(novelId, version, chunkSize, chunkOverlap);
        if (total <= 0) {
            throw new IllegalStateException("No scenes for novelId=" + novelId + " version=" + version
                    + " chunk=" + chunkSize + "/" + chunkOverlap);
        }
        syncVersionState(novelId, version, chunkSize, chunkOverlap, embedRunId);
        int totalScenes = (int) total;
        taskService.beginEmbedRun(taskId, embedRunId, totalScenes, "向量化：子任务投递中...");
        novelService.updateNovelStatus(novelId, NovelStatus.EMBEDDING);
        return totalScenes;
    }

    /**
     * 版本状态联动：读版本行（不存在则创建），startEmbed() 置 EMBEDDING，写入本 run 标识并清零游标。
     * <p>重跑/续跑（EMBEDDING/EMBED_DONE/PENDING/SPLITTING）同样允许重新进入 EMBEDDING，以支持重复向量化。</p>
     */
    private void syncVersionState(String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId) {
        NovelVersion v = novelVersionRepository.findById(novelId, version).orElseGet(() -> {
            NovelVersion created = NovelVersion.builder()
                    .novelId(novelId)
                    .versionTag(version)
                    .splitStrategy(SplitStrategy.OVERLAP_CHUNK)
                    .chunkSize(chunkSize)
                    .chunkOverlap(chunkOverlap)
                    // 场景已存在即切分已完成；此处创建的行直接进入可向量化状态
                    .status(VersionStatus.SPLIT_DONE)
                    .createdAt(System.currentTimeMillis())
                    .updatedAt(System.currentTimeMillis())
                    .build();
            novelVersionRepository.save(created);
            return created;
        });
        VersionStatus s = v.getStatus();
        if (s == VersionStatus.ACTIVE || s == VersionStatus.ABANDONED) {
            throw new IllegalStateException("终态版本不能重新向量化: " + s);
        }
        if (s == VersionStatus.SPLIT_DONE || s == VersionStatus.FAILED) {
            v.startEmbed();
        } else {
            // EMBEDDING / EMBED_DONE / PENDING / SPLITTING：重跑或续跑直接进入 EMBEDDING
            v.setStatus(VersionStatus.EMBEDDING);
            v.setUpdatedAt(System.currentTimeMillis());
        }
        v.setEmbedRunId(embedRunId);
        v.setEmbedCursorSceneSeq(0L);
        novelVersionRepository.save(v);
    }
}
