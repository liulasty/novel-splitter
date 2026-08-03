package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.pipeline.model.ResolvedChunkingParams;
import com.novel.splitter.pipeline.orchestrator.SplitNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.support.TaskFailureFormatter;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SplitWorker {

    private final SplitNovelUseCase splitNovelUseCase;
    private final TaskService taskService;
    private final RabbitTemplate rabbitTemplate;
    private final EmbedPipelineOrchestrator embedPipelineOrchestrator;

    private final NovelService novelService;
    private final NovelVersionRepository novelVersionRepository;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.batch-size:10}")
    private int batchSize;
    @org.springframework.beans.factory.annotation.Value("${splitter.enrich.enabled:false}")
    private boolean enrichEnabled;

    @RabbitListener(queues = RabbitConfig.SPLIT_TASK_QUEUE)
    public void processSplitTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        String novelId = null;
        String version = null;
        log.info("SplitWorker 接收到切分任务, taskId: {}", taskId);

        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.warn("任务 {} 在内存中不存在，可能由于服务重启，正在尝试自动重建...", taskId);
                TaskType tt = TaskType.SCENE_SPLIT;
                if (message.getTaskTypeForRecovery() != null && !message.getTaskTypeForRecovery().isBlank()) {
                    try {
                        tt = TaskType.valueOf(message.getTaskTypeForRecovery().trim());
                    } catch (IllegalArgumentException ignored) {
                        tt = TaskType.SCENE_SPLIT;
                    }
                }
                if (tt != TaskType.PIPELINE && tt != TaskType.SCENE_SPLIT) {
                    tt = TaskType.SCENE_SPLIT;
                }
                String fileName = novelService.getNovelById(message.getNovelId()).getFilePath();
                task = taskService.createTask(
                        taskId,
                        tt,
                        message.getNovelId(),
                        fileName,
                        message.getMaxScenes(),
                        message.getVersion()
                );
            }

            novelId = message.getNovelId();
            if (novelId == null || novelId.isBlank()) {
                throw new IllegalArgumentException("novelId must not be blank");
            }
            version = task.getVersion() != null && !task.getVersion().isBlank()
                    ? task.getVersion().trim()
                    : (message.getVersion() != null ? message.getVersion().trim() : "");
            if (version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }

            ResolvedChunkingParams chunkParams =
                    splitNovelUseCase.resolveChunkingParams(message.getChunkSize(), message.getChunkOverlap());

            // 版本行不存在则创建（PENDING，chunk 参数用解析后的有效值），再按游标续传。
            NovelVersion versionRow = resolveOrCreateVersion(novelId, version, chunkParams);
            if (isNotReenterable(versionRow)) {
                log.warn("版本 {}/{} 当前状态 {} 不允许重新切分，跳过任务 {}", novelId, version, versionRow.getStatus(), taskId);
                return;
            }
            int startChapterIndex = versionRow.getSplitCursorChapterIndex() != null
                    ? versionRow.getSplitCursorChapterIndex() + 1 : 0;
            long startSceneSeq = versionRow.getSplitCursorSceneSeq() != null ? versionRow.getSplitCursorSceneSeq() : 0L;

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 1,
                    "切分任务处理中（游标续传，从第 " + (startChapterIndex + 1) + " 章开始）...");
            versionRow.startSplit();
            novelVersionRepository.save(versionRow);

            String novelTitle = novelService.getNovelById(novelId).getTitle();

            SplitNovelUseCase.SplitProgress progress = splitNovelUseCase.split(
                    taskId,
                    novelId,
                    novelTitle,
                    task.getMaxScenes(),
                    version,
                    message.getChunkSize(),
                    message.getChunkOverlap(),
                    startChapterIndex,
                    startSceneSeq,
                    (p, info) -> taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, p, info));

            List<Long> sceneIds = progress.sceneIds();
            if (sceneIds == null || sceneIds.isEmpty()) {
                // 无新增场景：可能续传已到最后一章（或本次确无有效场景），均视为切分完成。
                log.warn("任务 {} 切分后没有新场景，视为切分完成（无重复写入）", taskId);
                versionRow.completeSplit();
                novelVersionRepository.save(versionRow);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分完成，无有效场景");
                if (novelId != null) {
                    novelService.updateNovelStatus(novelId, NovelStatus.SPLIT_COMPLETED);
                }
                return;
            }

            // 更新游标并置 SPLIT_DONE
            versionRow.advanceSplitCursor(progress.lastChapterIndex(), progress.lastSceneSeq());
            versionRow.completeSplit();
            novelVersionRepository.save(versionRow);

            // 更新总场景数
            task.setTotalScenes(sceneIds.size());
            task.getCompletedScenes().set(sceneIds.size());
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分阶段完成，数据已落盘");

            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.SPLIT_COMPLETED);
                if (message.isTriggerEmbed()) {
                    String embedTaskId = java.util.UUID.randomUUID().toString();
                    taskService.createTask(
                            embedTaskId,
                            TaskType.EMBED,
                            novelId,
                            novelId,
                            Integer.MAX_VALUE,
                            version
                    );
                    embedPipelineOrchestrator.startNewEmbedRun(
                            embedTaskId,
                            novelId,
                            version,
                            chunkParams.chunkSize(),
                            chunkParams.chunkOverlap());
                    log.info("任务 {} 已自动串联 EMBED 阶段（编排），embedTaskId={}", taskId, embedTaskId);
                }
                // 预留: 发送消息到 ENRICH_TASK_QUEUE 进行 AI 语义增强
                if (enrichEnabled) {
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.EXCHANGE_NAME,
                            "enrich",
                            new EnrichTaskMessage(taskId, novelId, version, sceneIds)
                    );
                    log.info("任务 {} 已发送 ENRICH 语义增强任务", taskId);
                }
            }

            log.info("任务 {} Split 阶段完成，共处理 {} 个场景", taskId, sceneIds.size());

        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            String failMsg = TaskFailureFormatter.format("SPLIT",
                    TaskFailureFormatter.params("novelId", message.getNovelId(), "taskId", taskId), e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, failMsg);
            if (message.getNovelId() != null) {
                novelService.updateNovelStatus(message.getNovelId(), NovelStatus.FAILED);
            }
            if (novelId != null && version != null) {
                markVersionFailed(novelId, version);
            }
        }
    }

    private NovelVersion resolveOrCreateVersion(String novelId, String version, ResolvedChunkingParams chunkParams) {
        return novelVersionRepository.findById(novelId, version).orElseGet(() -> {
            NovelVersion created = NovelVersion.builder()
                    .novelId(novelId)
                    .versionTag(version)
                    .splitStrategy(SplitStrategy.OVERLAP_CHUNK)
                    .chunkSize(chunkParams.chunkSize())
                    .chunkOverlap(chunkParams.chunkOverlap())
                    .status(VersionStatus.PENDING)
                    .createdAt(System.currentTimeMillis())
                    .updatedAt(System.currentTimeMillis())
                    .build();
            novelVersionRepository.save(created);
            return created;
        });
    }

    /**
     * 并发/状态乐观守卫：版本已进入切分之后的生命周期（向量化中/完成/激活/废弃）时，
     * 不允许再重入切分，直接跳过。PENDING / SPLITTING / SPLIT_DONE / FAILED 均允许
     * （含中断后从游标续传的场景）。
     */
    private boolean isNotReenterable(NovelVersion v) {
        VersionStatus s = v.getStatus();
        return s == VersionStatus.EMBEDDING
                || s == VersionStatus.EMBED_DONE
                || s == VersionStatus.ACTIVE
                || s == VersionStatus.ABANDONED;
    }

    private void markVersionFailed(String novelId, String version) {
        try {
            NovelVersion v = novelVersionRepository.findById(novelId, version).orElse(null);
            if (v != null) {
                v.fail();
                novelVersionRepository.save(v);
            }
        } catch (Exception ex) {
            log.warn("标记版本失败时出错 novelId={} version={}: {}", novelId, version, ex.toString());
        }
    }
}
