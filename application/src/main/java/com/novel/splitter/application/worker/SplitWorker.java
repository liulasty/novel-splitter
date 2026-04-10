package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.pipeline.orchestrator.SplitNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SplitWorker {

    private final SplitNovelUseCase splitNovelUseCase;
    private final TaskService taskService;
    private final RabbitTemplate rabbitTemplate;

    private final NovelService novelService;
    private final SceneRepository sceneRepository;
    private final VectorStore vectorStore;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.batch-size:10}")
    private int batchSize;
    @org.springframework.beans.factory.annotation.Value("${splitter.enrich.enabled:false}")
    private boolean enrichEnabled;

    @RabbitListener(queues = RabbitConfig.SPLIT_TASK_QUEUE)
    public void processSplitTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        log.info("SplitWorker 接收到切分任务, taskId: {}", taskId);
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.warn("任务 {} 在内存中不存在，可能由于服务重启，正在尝试自动重建...", taskId);
                task = taskService.createTask(
                        taskId,
                        TaskType.SPLIT,
                        message.getNovelId(),
                        message.getNovelId(),
                        message.getMaxScenes(),
                        message.getVersion()
                );
            }

            String novelId = message.getNovelId();
            if (novelId == null || novelId.isBlank()) {
                throw new IllegalArgumentException("novelId must not be blank");
            }
            String version = task.getVersion() != null && !task.getVersion().isBlank()
                    ? task.getVersion().trim()
                    : (message.getVersion() != null ? message.getVersion().trim() : "");
            if (version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }

            // P2: strict idempotent cleanup (DB + Chroma). Either failure blocks processing.
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 1, "幂等清理：删除旧场景与旧向量...");
            try {
                sceneRepository.deleteVersionByNovelId(novelId, version);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to cleanup DB scenes for novelId=" + novelId + " version=" + version, e);
            }
            try {
                vectorStore.delete(Map.of("novelId", novelId, "version", version));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to cleanup Chroma vectors for novelId=" + novelId + " version=" + version, e);
            }

            String novelTitle = novelId != null ? novelService.getNovelById(novelId).getTitle() : null;

            List<Long> sceneIds = splitNovelUseCase.split(taskId, novelId, novelTitle, task.getMaxScenes(), version, (progress, info) -> {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });

            if (sceneIds == null || sceneIds.isEmpty()) {
                log.warn("任务 {} 切分后没有场景，直接标记为成功", taskId);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分完成，无有效场景");
                if (message.getNovelId() != null) {
                    novelService.updateNovelStatus(message.getNovelId(), NovelStatus.SPLIT_COMPLETED);
                }
                return;
            }

            // 更新总场景数
            task.setTotalScenes(sceneIds.size());
            task.getCompletedScenes().set(sceneIds.size());
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分阶段完成，数据已落盘");
            
            if (message.getNovelId() != null) {
                novelService.updateNovelStatus(message.getNovelId(), NovelStatus.SPLIT_COMPLETED);
                if (message.isTriggerEmbed()) {
                    String embedTaskId = java.util.UUID.randomUUID().toString();
                    taskService.createTask(
                            embedTaskId,
                            TaskType.EMBED,
                            message.getNovelId(),
                            message.getNovelId(),
                            Integer.MAX_VALUE,
                            message.getVersion()
                    );
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.EXCHANGE_NAME,
                            "embed",
                            new com.novel.splitter.domain.task.EmbedTaskMessage(embedTaskId, message.getNovelId(), message.getVersion())
                    );
                    log.info("任务 {} 已自动串联 EMBED 阶段，embedTaskId={}", taskId, embedTaskId);
                }
                // 预留: 发送消息到 ENRICH_TASK_QUEUE 进行 AI 语义增强
                if (enrichEnabled) {
                    rabbitTemplate.convertAndSend(
                            RabbitConfig.EXCHANGE_NAME,
                            "enrich",
                            new EnrichTaskMessage(taskId, message.getNovelId(), message.getVersion(), sceneIds)
                    );
                    log.info("任务 {} 已发送 ENRICH 语义增强任务", taskId);
                }
            }
            
            log.info("任务 {} Split 阶段完成，共处理 {} 个场景", taskId, sceneIds.size());
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "切分失败: " + e.getMessage());
            if (message.getNovelId() != null) {
                novelService.updateNovelStatus(message.getNovelId(), NovelStatus.FAILED);
            }
        }
    }
}
