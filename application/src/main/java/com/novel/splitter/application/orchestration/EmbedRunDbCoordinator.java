package com.novel.splitter.application.orchestration;

import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向量化编排中「Chroma 删除之后」的数据库步骤：单事务提交，避免场景重置与任务行更新之间出现部分成功。
 * <p>
 * 与向量库之间无法做真正 2PC；若本事务提交前 Chroma 删除已成功而此处失败，需依赖消息重投或人工重新触发向量化（删除为幂等）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EmbedRunDbCoordinator {

    private final SceneRepository sceneRepository;
    private final TaskService taskService;
    private final NovelService novelService;

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
        int totalScenes = (int) total;
        taskService.beginEmbedRun(taskId, embedRunId, totalScenes, "向量化：子任务投递中...");
        novelService.updateNovelStatus(novelId, NovelStatus.EMBEDDING);
        return totalScenes;
    }
}
