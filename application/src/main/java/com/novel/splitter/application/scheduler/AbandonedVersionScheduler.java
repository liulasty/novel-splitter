package com.novel.splitter.application.scheduler;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.domain.task.CleanupTaskMessage;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时停滞版本废弃回收调度器。
 * <p>
 * 周期扫描 SPLITTING/EMBEDDING 状态、updatedAt 超时（默认 2 小时）未推进的版本，
 * 将其标记 ABANDONED 并投递 CleanupTask 回收该版本专属向量集合（scenes 保留）。
 * 心跳（advanceSplitCursor / embed 游标推进）会刷新 updatedAt，避免误杀进行中的长任务。
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AbandonedVersionScheduler {

    /** 超时阈值：超过 2 小时未推进心跳视为停滞。 */
    private static final long STALLED_THRESHOLD_MS = 2 * 60 * 60 * 1000L;

    private static final List<VersionStatus> SCAN_STATUSES = List.of(VersionStatus.SPLITTING, VersionStatus.EMBEDDING);

    private final NovelVersionRepository novelVersionRepository;
    private final CleanupTaskRepository cleanupTaskRepository;
    private final TaskQueuePort taskQueuePort;

    /** 默认每 30 分钟扫描一次；可通过 {@code splitter.abandoned-version.cron} 覆盖。 */
    @Scheduled(cron = "${splitter.abandoned-version.cron:0 */30 * * * ?}")
    public void scan() {
        long now = System.currentTimeMillis();
        long threshold = now - STALLED_THRESHOLD_MS;
        List<NovelVersion> stalled = novelVersionRepository.findStalled(SCAN_STATUSES, threshold);
        for (NovelVersion v : stalled) {
            // 边界防御：findStalled 查询与 isStalled 判定语义一致；恰好等于阈值不废弃（严格 >）
            if (!v.isStalled(now, STALLED_THRESHOLD_MS)) {
                continue;
            }
            v.abandon();
            novelVersionRepository.save(v);

            CleanupTask task = CleanupTask.builder()
                    .targetId(v.getNovelId())
                    .targetType("VERSION")
                    .version(v.getVersionTag())
                    .status("PENDING")
                    .build();
            CleanupTask savedTask = cleanupTaskRepository.save(task);

            CleanupTaskMessage message = CleanupTaskMessage.builder()
                    .cleanupTaskId(savedTask.getId())
                    .targetId(v.getNovelId())
                    .targetType("VERSION")
                    .novelId(v.getNovelId())
                    .version(v.getVersionTag())
                    .build();
            taskQueuePort.sendCleanup(message);

            String collectionName = VectorStore.collectionNameFor(v.getNovelId(), v.getVersionTag());
            log.warn("废弃停滞版本 novelId={} versionTag={} collection={}",
                    v.getNovelId(), v.getVersionTag(), collectionName);
        }
    }
}
