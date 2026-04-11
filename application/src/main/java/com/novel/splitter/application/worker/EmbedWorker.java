package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.support.TaskFailureFormatter;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmbedWorker {

    private final EmbedNovelUseCase embedNovelUseCase;
    private final TaskService taskService;
    private final SceneRepository sceneRepository;
    private final EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.embedding-rate-limit.enabled:false}")
    private boolean embeddingRateLimitEnabled;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.embedding-rate-limit.max-batches:0}")
    private int embeddingMaxBatchesPerWindow;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.embedding-rate-limit.duration-seconds:60}")
    private int embeddingRateLimitDurationSeconds;

    /**
     * 过渡期：粗粒度消息仅转调编排（delete + fan-out），不在此循环分页 embed。
     */
    @RabbitListener(queues = RabbitConfig.EMBED_TASK_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void processEmbedTask(EmbedTaskMessage message) {
        embedPipelineOrchestrator.handleLegacyEmbedTaskMessage(message);
    }

    @RabbitListener(queues = RabbitConfig.EMBED_SCENE_TASK_QUEUE, containerFactory = "embedSceneBatchListenerContainerFactory")
    public void onEmbedSceneBatch(List<EmbedSceneTaskMessage> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        RateLimiter limiter = createEmbeddingBatchLimiter();
        for (EmbedSceneTaskMessage m : batch) {
            if (limiter != null && !limiter.tryAcquire(1, 30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Embedding batch rate limiter timeout while waiting for permit");
            }
            processEmbedScene(m);
        }
    }

    private void processEmbedScene(EmbedSceneTaskMessage message) {
        String taskId = message.getTaskId();
        String novelId = message.getNovelId();
        String version = message.getVersion();
        String embedRunId = message.getEmbedRunId();
        Long scenePid = message.getScenePersistenceId();

        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }
            if (task.getStatus() == SplitTask.TaskStatus.SUCCESS || task.getStatus() == SplitTask.TaskStatus.FAILED) {
                return;
            }
            if (embedRunId == null || !embedRunId.equals(task.getCurrentEmbedRunId())) {
                log.info("丢弃过期 embed 子任务 taskId={} msgRun={} currentRun={}", taskId, embedRunId, task.getCurrentEmbedRunId());
                return;
            }

            List<Scene> scenes = sceneRepository.findByIds(List.of(scenePid));
            if (scenes == null || scenes.isEmpty()) {
                log.warn("Scene id {} 不存在，跳过", scenePid);
                return;
            }
            Scene sc = scenes.get(0);
            if (sc.getEmbedStatus() == EmbedStatus.SUCCESS
                    && embedRunId.equals(sc.getEmbedRunId())) {
                return;
            }

            embedNovelUseCase.embedBatch(List.of(scenePid));
            sceneRepository.updateEmbedOutcome(scenePid, embedRunId, EmbedStatus.SUCCESS, null);
        } catch (Exception e) {
            log.error("embed scene failed taskId={} scenePid={}", taskId, scenePid, e);
            String err = TaskFailureFormatter.format("EMBED_SCENE",
                    TaskFailureFormatter.params(
                            "novelId", novelId,
                            "version", version,
                            "taskId", taskId,
                            "sceneId", scenePid != null ? scenePid.toString() : "null"),
                    e);
            try {
                if (embedRunId != null && scenePid != null) {
                    sceneRepository.updateEmbedOutcome(scenePid, embedRunId, EmbedStatus.FAILED, err);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to persist embed failure for scene {}: {}", scenePid, ex.toString());
            }
            throw new RuntimeException("Embed scene failed", e);
        }
    }

    private RateLimiter createEmbeddingBatchLimiter() {
        if (!embeddingRateLimitEnabled || embeddingMaxBatchesPerWindow <= 0) {
            return null;
        }
        double windowSec = Math.max(1, embeddingRateLimitDurationSeconds);
        double permitsPerSecond = embeddingMaxBatchesPerWindow / windowSec;
        return RateLimiter.create(Math.max(permitsPerSecond, 1.0e-9d));
    }
}
