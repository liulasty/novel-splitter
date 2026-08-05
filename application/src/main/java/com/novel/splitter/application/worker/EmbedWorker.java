package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.support.TaskFailureFormatter;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.embedding.api.VectorStore;
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
    private final NovelVersionRepository novelVersionRepository;

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
        int chunkSize = sample.getChunkSize();
        int chunkOverlap = sample.getChunkOverlap();

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
                processEmbedSubBatch(subIds, sceneByPid, taskId, novelId, version, chunkSize, chunkOverlap, embedRunId);
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
            int chunkSize,
            int chunkOverlap,
            String embedRunId) {
        try {
            String colName = VectorStore.collectionNameFor(novelId, version);
            List<Long> written = embedNovelUseCase.embedBatch(subIds, colName);
            if (written.isEmpty()) {
                markNoWriteOutcomes(subIds, sceneByPid, embedRunId);
            } else {
                sceneRepository.batchUpdateEmbedOutcome(written, embedRunId, EmbedStatus.SUCCESS, null);
                log.debug("子批次向量化完成：本批写入 {}/{} 个场景 taskId={} embedRunId={}",
                        written.size(), subIds.size(), taskId, embedRunId);
                // 推进版本向量化游标，并判定该 run 是否全量完成 → EMBED_DONE
                advanceEmbedCursorAndCheckCompletion(
                        novelId, version, chunkSize, chunkOverlap, embedRunId, maxSeqOf(sceneByPid, written));
            }
        } catch (Exception e) {
            log.error("子批次向量化失败 taskId={} embedRunId={} subBatchSize={} scenePids={}",
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
            failVersion(novelId, version);
        }
    }

    private long maxSeqOf(Map<Long, Scene> sceneByPid, List<Long> pids) {
        long max = 0L;
        for (Long pid : pids) {
            Scene s = sceneByPid.get(pid);
            if (s != null && s.getSeq() != null) {
                max = Math.max(max, s.getSeq());
            }
        }
        return max;
    }

    /**
     * 推进 NovelVersion.embedCursorSceneSeq（取该批已成功 scene 的最大 seq，不回退），
     * 并在该 run 下 SUCCESS 计数达到 profile 总量时置 EMBED_DONE。best-effort：任何异常仅记日志。
     */
    private void advanceEmbedCursorAndCheckCompletion(
            String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId, long maxSeq) {
        try {
            NovelVersion v = novelVersionRepository.findById(novelId, version).orElse(null);
            if (v == null) {
                return;
            }
            long cur = v.getEmbedCursorSceneSeq() == null ? 0L : v.getEmbedCursorSceneSeq();
            v.setEmbedCursorSceneSeq(Math.max(cur, maxSeq));

            // 写入集合名（首次落盘后即可查询）
            if (v.getCollectionName() == null || v.getCollectionName().isBlank()) {
                v.setCollectionName(VectorStore.collectionNameFor(novelId, version));
            }

            long success = sceneRepository.countEmbedByRunAndStatus(
                    novelId, version, chunkSize, chunkOverlap, embedRunId, EmbedStatus.SUCCESS);
            long total = sceneRepository.countByProfile(novelId, version, chunkSize, chunkOverlap);
            if (total > 0) {
                int pct = (int) Math.min(100, success * 100L / total);
                log.info("向量化进度：{}/{}（{}%）novelId={} version={}", success, total, pct, novelId, version);
            }
            if (total > 0 && success >= total) {
                if (v.getStatus() == VersionStatus.EMBEDDING) {
                    v.completeEmbed();
                    log.info("版本 {}/{} 向量化全部完成（{} 个场景）-> EMBED_DONE", novelId, version, success);
                } else if (v.getStatus() == VersionStatus.FAILED) {
                    // 失败后有批次补跑成功且全量达成：直接升级 EMBED_DONE（保留游标）
                    v.setStatus(VersionStatus.EMBED_DONE);
                }
            }
            novelVersionRepository.save(v);
        } catch (Exception e) {
            log.warn("Failed to advance embed cursor novelId={} version={}: {}", novelId, version, e.toString());
        }
    }

    /** 单批 embed 失败 → 版本标记 FAILED（保留已向量化批次与游标，可续传）。best-effort。 */
    private void failVersion(String novelId, String version) {
        try {
            NovelVersion v = novelVersionRepository.findById(novelId, version).orElse(null);
            if (v != null) {
                v.fail();
                novelVersionRepository.save(v);
                log.warn("版本 {}/{} 标记 FAILED（可续传）", novelId, version);
            }
        } catch (Exception e) {
            log.warn("Failed to mark version failed novelId={} version={}: {}", novelId, version, e.toString());
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
