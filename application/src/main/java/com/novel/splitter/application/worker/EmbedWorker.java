package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.support.TaskFailureFormatter;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.VectorStore;
import com.google.common.util.concurrent.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmbedWorker {

    private final EmbedNovelUseCase embedNovelUseCase;
    private final TaskService taskService;
    private final SceneRepository sceneRepository;
    private final NovelService novelService;
    private final VectorStore vectorStore;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.batch-size:100}")
    private int batchSize;

    @org.springframework.beans.factory.annotation.Value("${llm.coze.rate-limit.max-requests:2}")
    private int maxRequests;

    @org.springframework.beans.factory.annotation.Value("${llm.coze.rate-limit.duration-seconds:60}")
    private int durationSeconds;

    @RabbitListener(queues = RabbitConfig.EMBED_TASK_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void processEmbedTask(EmbedTaskMessage message) {
        String taskId = message.getTaskId();
        String novelId = message.getNovelId();
        String version = message.getVersion();

        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            if (task.getStatus() == SplitTask.TaskStatus.FAILED || task.getStatus() == SplitTask.TaskStatus.SUCCESS) {
                return;
            }

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 0, "向量化开始：前置幂等清理（失败将阻断任务）...");
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.EMBEDDING);
            }

            if (novelId == null || novelId.isBlank()) {
                throw new IllegalArgumentException("novelId must not be blank");
            }
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("version must not be blank");
            }

            int[] profile = resolveEmbedProfile(novelId, version, message.getChunkSize(), message.getChunkOverlap());
            int chunkSize = profile[0];
            int chunkOverlap = profile[1];

            try {
                vectorStore.delete(Map.of(
                        "novelId", novelId,
                        "version", version,
                        "chunkSize", chunkSize,
                        "chunkOverlap", chunkOverlap));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to cleanup vectors for novelId=" + novelId + " version=" + version, e);
            }

            int page = 0;
            int totalScenesProcessed = 0;
            long totalScenes = 0;
            double permitsPerSecond = Math.max(1.0d / 60.0d, maxRequests / (double) Math.max(1, durationSeconds));
            RateLimiter rateLimiter = RateLimiter.create(permitsPerSecond);

            while (true) {
                PagedResult<com.novel.splitter.domain.model.Scene> scenePage =
                        sceneRepository.findByProfile(novelId, version, chunkSize, chunkOverlap, PageQuery.of(page, batchSize));
                if (page == 0) {
                    totalScenes = scenePage.getTotalElements();
                    task.setTotalScenes((int) totalScenes);
                    if (totalScenes == 0) {
                        throw new IllegalStateException("No scenes for novelId=" + novelId + " version=" + version
                                + " chunk=" + chunkSize + "/" + chunkOverlap + "; run split first");
                    }
                }

                List<Long> sceneIds = scenePage.getContent().stream()
                        .map(com.novel.splitter.domain.model.Scene::getPersistenceId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());

                if (sceneIds.isEmpty()) {
                    break;
                }

                if (!rateLimiter.tryAcquire(1, 30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Embedding rate limiter timeout while waiting for permit");
                }
                embedNovelUseCase.embedBatch(sceneIds);
                totalScenesProcessed += sceneIds.size();

                int progress = (int) ((totalScenesProcessed / (double) totalScenes) * 100);
                String info = String.format("向量化中：%d/%d", totalScenesProcessed, totalScenes);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);

                if (!scenePage.hasNext()) {
                    break;
                }
                page++;
            }

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "入库完成");
            log.info("任务 {} 处理成功", taskId);

            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.COMPLETED);
            }

        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            String failMsg = TaskFailureFormatter.format("EMBED",
                    TaskFailureFormatter.params("novelId", novelId, "version", version, "taskId", taskId), e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, failMsg);
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.FAILED);
            }
        }
    }

    /**
     * @return int[0]=chunkSize, int[1]=chunkOverlap
     */
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
