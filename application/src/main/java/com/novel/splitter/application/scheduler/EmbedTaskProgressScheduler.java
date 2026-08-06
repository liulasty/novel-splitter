package com.novel.splitter.application.scheduler;

import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 聚合 EMBED 任务进度（由 scenes 表 embed_status 统计），并在无待处理场景时落终态。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "splitter.embed.progress-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class EmbedTaskProgressScheduler {

    private final TaskService taskService;
    private final SceneRepository sceneRepository;
    private final NovelService novelService;

    @Scheduled(fixedDelayString = "${splitter.embed.progress-interval-ms:5000}")
    public void aggregateEmbedProgress() {
        PagedResult<SplitTask> page = taskService.findTasksFiltered(
                SplitTaskFilter.normalized(null, TaskType.EMBED, SplitTask.TaskStatus.PROCESSING, null, null, 0, 200));
        for (SplitTask task : page.getContent()) {
            try {
                processOne(task);
            } catch (RuntimeException ex) {
                log.warn("embed 进度聚合失败 taskId={} : {}", task.getTaskId(), ex.toString());
            }
        }
    }

    private void processOne(SplitTask task) {
        String runId = task.getCurrentEmbedRunId();
        if (runId == null || runId.isBlank()) {
            return;
        }
        String novelId = task.getNovelId();
        String version = task.getVersion();
        int total = task.getTotalScenes();
        if (total <= 0) {
            return;
        }
        int[] profile = sceneRepository.resolveChunkProfileForEmbedRun(novelId, version, runId).orElse(null);
        if (profile == null) {
            return;
        }
        long ok = sceneRepository.countEmbedByRunAndStatus(
                novelId, version, profile[0], profile[1], runId, EmbedStatus.SUCCESS);
        long fail = sceneRepository.countEmbedByRunAndStatus(
                novelId, version, profile[0], profile[1], runId, EmbedStatus.FAILED);
        long pend = sceneRepository.countEmbedByRunAndStatus(
                novelId, version, profile[0], profile[1], runId, EmbedStatus.PENDING);

        if (pend > 0) {
            taskService.updateEmbedProcessingProgress(task.getTaskId(), ok, fail, total);
            return;
        }

        if (fail > 0) {
            String msg = "向量化未全部成功：失败 " + fail + " 个场景，请查看 scenes.embed_error 或 DLQ";
            taskService.updateTaskStatus(task.getTaskId(), SplitTask.TaskStatus.FAILED, 0, msg);
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.FAILED);
            }
            return;
        }

        if (ok >= total) {
            taskService.updateTaskStatus(task.getTaskId(), SplitTask.TaskStatus.SUCCESS, 100, "向量化完成");
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.COMPLETED);
            }
        }
    }
}
