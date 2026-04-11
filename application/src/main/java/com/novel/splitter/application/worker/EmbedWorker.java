package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.support.TaskFailureFormatter;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmbedWorker {

    private final EmbedNovelUseCase embedNovelUseCase;
    private final TaskService taskService;
    private final SceneRepository sceneRepository;
    private final EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @Value("${splitter.ingestion.embedding-rate-limit.enabled:false}")
    private boolean embeddingRateLimitEnabled;

    @Value("${splitter.ingestion.embedding-rate-limit.max-batches:0}")
    private int embeddingMaxBatchesPerWindow;

    @Value("${splitter.ingestion.embedding-rate-limit.duration-seconds:60}")
    private int embeddingRateLimitDurationSeconds;

    @Value("${splitter.embed.sub-batch-size:16}")
    private int embedSubBatchSize;

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
        if (limiter != null && !limiter.tryAcquire(1, 30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Embedding batch rate limiter timeout while waiting for permit");
        }

        Map<String, List<EmbedSceneTaskMessage>> groups = new LinkedHashMap<>();
        for (EmbedSceneTaskMessage m : batch) {
            String runId = m.getEmbedRunId();
            if (runId == null) {
                log.warn("丢弃 embed 子任务：embedRunId 为空 taskId={} scenePid={}", m.getTaskId(), m.getScenePersistenceId());
                continue;
            }
            String key = m.getTaskId() + "\0" + runId;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }

        for (List<EmbedSceneTaskMessage> group : groups.values()) {
            processEmbedSceneGroup(group);
        }
    }

    private void processEmbedSceneGroup(List<EmbedSceneTaskMessage> group) {
        if (group == null || group.isEmpty()) {
            return;
        }
        EmbedSceneTaskMessage sample = group.get(0);
        String taskId = sample.getTaskId();
        String novelId = sample.getNovelId();
        String version = sample.getVersion();
        String embedRunId = sample.getEmbedRunId();

        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在，跳过本组 {} 条", taskId, group.size());
                return;
            }
            if (task.getStatus() == SplitTask.TaskStatus.SUCCESS || task.getStatus() == SplitTask.TaskStatus.FAILED) {
                return;
            }
            if (!embedRunId.equals(task.getCurrentEmbedRunId())) {
                log.info("丢弃过期 embed 子任务组 taskId={} msgRun={} currentRun={} count={}",
                        taskId, embedRunId, task.getCurrentEmbedRunId(), group.size());
                return;
            }

            LinkedHashSet<Long> pidSet = new LinkedHashSet<>();
            for (EmbedSceneTaskMessage m : group) {
                Long pid = m.getScenePersistenceId();
                if (pid == null) {
                    log.warn("scenePersistenceId 为空，跳过 taskId={}", taskId);
                    continue;
                }
                pidSet.add(pid);
            }
            if (pidSet.isEmpty()) {
                return;
            }
            List<Long> orderedPids = new ArrayList<>(pidSet);

            List<Scene> scenes = sceneRepository.findByIds(orderedPids);
            Map<Long, Scene> sceneByPid = new LinkedHashMap<>();
            for (Scene s : scenes) {
                if (s.getPersistenceId() != null) {
                    sceneByPid.put(s.getPersistenceId(), s);
                }
            }

            List<Long> toEmbed = new ArrayList<>();
            for (Long pid : orderedPids) {
                Scene sc = sceneByPid.get(pid);
                if (sc != null
                        && sc.getEmbedStatus() == EmbedStatus.SUCCESS
                        && embedRunId.equals(sc.getEmbedRunId())) {
                    continue;
                }
                toEmbed.add(pid);
            }
            if (toEmbed.isEmpty()) {
                return;
            }

            int subSize = Math.max(1, embedSubBatchSize);
            for (int i = 0; i < toEmbed.size(); i += subSize) {
                List<Long> subIds = toEmbed.subList(i, Math.min(i + subSize, toEmbed.size()));
                processEmbedSubBatch(subIds, sceneByPid, taskId, novelId, version, embedRunId);
            }
        } catch (Exception e) {
            log.error("embed scene group failed taskId={}", taskId, e);
            // Do not rethrow: batched listener must not nack the whole batch for one group failure.
        }
    }

    private void processEmbedSubBatch(
            List<Long> subIds,
            Map<Long, Scene> sceneByPid,
            String taskId,
            String novelId,
            String version,
            String embedRunId) {
        try {
            List<Long> written = embedNovelUseCase.embedBatch(subIds);
            if (written.isEmpty()) {
                markNoWriteOutcomes(subIds, sceneByPid, embedRunId);
            } else {
                sceneRepository.batchUpdateEmbedOutcome(written, embedRunId, EmbedStatus.SUCCESS, null);
                log.debug("embed sub-batch ok taskId={} embedRunId={} subBatchSize={} written={}",
                        taskId, embedRunId, subIds.size(), written.size());
            }
        } catch (Exception e) {
            log.error("embed sub-batch failed taskId={} embedRunId={} subBatchSize={} scenePids={}",
                    taskId, embedRunId, subIds.size(), subIds, e);
            String err = TaskFailureFormatter.format("EMBED_SCENE",
                    TaskFailureFormatter.params(
                            "novelId", novelId,
                            "version", version,
                            "taskId", taskId,
                            "scenePersistenceIds", subIds.stream().map(String::valueOf).collect(Collectors.joining(","))),
                    e);
            try {
                sceneRepository.batchUpdateEmbedOutcome(subIds, embedRunId, EmbedStatus.FAILED, err);
            } catch (RuntimeException ex) {
                log.warn("Failed to persist embed failure batch scenePids={}: {}", subIds, ex.toString());
            }
        }
    }

    private void markNoWriteOutcomes(List<Long> subIds, Map<Long, Scene> sceneByPid, String embedRunId) {
        for (Long pid : subIds) {
            Scene sc = sceneByPid.get(pid);
            String reason;
            if (sc == null) {
                reason = "Scene row not found for embed.";
            } else if (sc.getText() == null || sc.getText().trim().isEmpty()) {
                reason = "Empty or blank scene text; skipping embed.";
            } else {
                reason = "No embedding persisted (batch produced zero writes).";
            }
            try {
                sceneRepository.updateEmbedOutcome(pid, embedRunId, EmbedStatus.FAILED, reason);
            } catch (RuntimeException ex) {
                log.warn("Failed to persist embed skip/fail for scene {}: {}", pid, ex.toString());
            }
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
