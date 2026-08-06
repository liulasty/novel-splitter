package com.novel.splitter.application.orchestration;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 向量化编排：每轮 run 仅在此处执行 vector delete、场景行重置与 MQ fan-out；消费端只做单场景 embed。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbedPipelineOrchestrator {

    private final TaskService taskService;
    private final SceneRepository sceneRepository;
    private final VectorStore vectorStore;
    private final TaskQueuePort taskQueuePort;
    private final EmbedRunDbCoordinator embedRunDbCoordinator;
    private final NovelVersionRepository novelVersionRepository;

    @Value("${splitter.embed.scene-publish-batch-size:200}")
    private int scenePublishBatchSize;

    /**
     * API / SplitWorker 新任务入口：delete 一次、重置场景、投递细粒度消息。
     */
    public void startNewEmbedRun(String taskId, String novelId, String version, Integer chunkSize, Integer chunkOverlap) {
        SplitTask task = taskService.getTask(taskId);
        if (task == null) {
            log.warn("跳过 embed 编排：任务行已不存在（可能是 purge/删除后遗留的过期 MQ 消息）。taskId={}", taskId);
            return;
        }
        if (task.getStatus() == SplitTask.TaskStatus.SUCCESS || task.getStatus() == SplitTask.TaskStatus.FAILED) {
            log.warn("任务 {} 已处于终态 {}，跳过 embed 编排", taskId, task.getStatus());
            return;
        }
        if (task.getStatus() == SplitTask.TaskStatus.PROCESSING
                && task.getCurrentEmbedRunId() != null
                && !task.getCurrentEmbedRunId().isBlank()
                && task.getTotalScenes() > 0) {
            log.info("embed 任务 {} 已有进行中的 run {}，忽略重复触发", taskId, task.getCurrentEmbedRunId());
            return;
        }

        int[] profile = resolveEmbedProfile(novelId, version, chunkSize, chunkOverlap);
        int cs = profile[0];
        int co = profile[1];

        String embedRunId = UUID.randomUUID().toString();

        String colName = VectorStore.collectionNameFor(novelId, version);
        try {
            vectorStore.deleteByCollection(colName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to cleanup vectors for novelId=" + novelId + " version=" + version
                    + " collection=" + colName, e);
        }

        int totalScenes = embedRunDbCoordinator.beginRunAfterVectorsCleaned(taskId, novelId, version, cs, co, embedRunId);

        fanOutSceneMessages(taskId, novelId, version, cs, co, embedRunId);
        log.info("embed run 已启动 taskId={} embedRunId={} totalScenes={}", taskId, embedRunId, totalScenes);
    }

    /**
     * 兼容队列中旧 {@link EmbedTaskMessage}：与 {@link #startNewEmbedRun} 相同。
     */
    public void handleLegacyEmbedTaskMessage(EmbedTaskMessage message) {
        startNewEmbedRun(
                message.getTaskId(),
                message.getNovelId(),
                message.getVersion(),
                message.getChunkSize(),
                message.getChunkOverlap());
    }

    /**
     * 续跑：不 delete；仅对当前 run 下仍为 PENDING/FAILED 的场景补投 MQ。
     */
    public void resumeEmbedRun(String taskId) {
        SplitTask task = taskService.getTask(taskId);
        if (task == null) {
            log.warn("跳过 embed 续跑：任务行已不存在（过期 MQ 消息？）。taskId={}", taskId);
            return;
        }
        String runId = task.getCurrentEmbedRunId();
        if (runId == null || runId.isBlank()) {
            throw new IllegalStateException("Task has no current embed run id: " + taskId);
        }
        String novelId = task.getNovelId();
        String version = task.getVersion();
        int[] profile = sceneRepository.resolveChunkProfileForEmbedRun(novelId, version, runId)
                .orElseThrow(() -> new IllegalStateException("Cannot resolve chunk profile for embed run " + runId));
        List<Long> ids = sceneRepository.listPersistenceIdsForEmbedResume(
                novelId, version, profile[0], profile[1], runId);
        long cursor = embedCursorSceneSeq(novelId, version);
        if (cursor > 0 && !ids.isEmpty()) {
            // 只补投游标之后的 PENDING/FAILED scene（游标表示已向量化的全局 seq 水位）
            List<Scene> scenes = sceneRepository.findByIds(ids);
            ids = scenes.stream()
                    .filter(s -> s.getPersistenceId() != null && s.getSeq() != null && s.getSeq() > cursor)
                    .map(Scene::getPersistenceId)
                    .toList();
        }
        if (ids.isEmpty()) {
            log.info("任务 {} 没有待续跑的 PENDING/FAILED 场景", taskId);
            return;
        }
        fanOutBatch(taskId, novelId, version, profile[0], profile[1], runId, ids);
        log.info("续跑已投递 {} 条场景消息 taskId={}", ids.size(), taskId);
    }

    private void fanOutSceneMessages(
            String taskId, String novelId, String version, int chunkSize, int chunkOverlap, String embedRunId) {
        List<Long> ids = sceneRepository.listPersistenceIdsByProfile(novelId, version, chunkSize, chunkOverlap);
        fanOutBatch(taskId, novelId, version, chunkSize, chunkOverlap, embedRunId, ids);
    }

    private void fanOutBatch(
            String taskId,
            String novelId,
            String version,
            int chunkSize,
            int chunkOverlap,
            String embedRunId,
            List<Long> ids) {
        int batch = Math.max(1, scenePublishBatchSize);
        List<EmbedSceneTaskMessage> buf = new ArrayList<>(batch);
        for (Long sid : ids) {
            buf.add(new EmbedSceneTaskMessage(taskId, novelId, version, chunkSize, chunkOverlap, embedRunId, sid));
            if (buf.size() >= batch) {
                taskQueuePort.sendEmbedScenes(buf);
                buf = new ArrayList<>(batch);
            }
        }
        if (!buf.isEmpty()) {
            taskQueuePort.sendEmbedScenes(buf);
        }
    }

    /** 读取版本向量化游标；无版本行或游标为 null 视为 0（从头补投）。 */
    private long embedCursorSceneSeq(String novelId, String version) {
        NovelVersion v = novelVersionRepository.findById(novelId, version).orElse(null);
        return v != null && v.getEmbedCursorSceneSeq() != null ? v.getEmbedCursorSceneSeq() : 0L;
    }

    private int[] resolveEmbedProfile(String novelId, String version, Integer msgChunkSize, Integer msgChunkOverlap) {
        if (msgChunkSize != null && msgChunkOverlap != null) {
            return new int[] {msgChunkSize, msgChunkOverlap};
        }
        List<SceneSplitProfile> candidates = sceneRepository.listSplitProfilesByNovelId(novelId).stream()
                .filter(p -> version.equals(p.version()))
                .filter(p -> p.chunkSize() != null && p.chunkOverlap() != null)
                .toList();
        if (candidates.size() == 1) {
            SceneSplitProfile p = candidates.get(0);
            return new int[] {p.chunkSize(), p.chunkOverlap()};
        }
        if (candidates.isEmpty()) {
            List<SceneSplitProfile> any = sceneRepository.listSplitProfilesByNovelId(novelId).stream()
                    .filter(p -> version.equals(p.version()))
                    .toList();
            if (any.size() == 1 && (any.get(0).chunkSize() == null || any.get(0).chunkOverlap() == null)) {
                throw new IllegalStateException(
                        "场景数据缺少 chunk_size/chunk_overlap，请重新执行场景切分或执行 DB 回填后再向量化。novelId="
                                + novelId + ", version=" + version);
            }
        }
        throw new IllegalArgumentException(
                "向量化需指定 chunkSize、chunkOverlap 查询参数（或与 POST /embed 等价字段），因版本 "
                        + version + " 下存在多套滑窗分区。novelId=" + novelId);
    }
}
