package com.novel.splitter.application.scheduler;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.domain.task.CleanupTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时废弃回收调度器契约测试。
 */
@ExtendWith(MockitoExtension.class)
class AbandonedVersionSchedulerTest {

    @Mock private NovelVersionRepository novelVersionRepository;
    @Mock private CleanupTaskRepository cleanupTaskRepository;
    @Mock private TaskQueuePort taskQueuePort;

    @InjectMocks
    private AbandonedVersionScheduler scheduler;

    private static final long HOUR_MS = 60 * 60 * 1000L;

    private NovelVersion version(VersionStatus status, long updatedAtMsAgo) {
        return NovelVersion.builder()
                .novelId("n1")
                .versionTag("v1")
                .status(status)
                .updatedAt(System.currentTimeMillis() - updatedAtMsAgo)
                .build();
    }

    @Test
    void staleEmbeddingVersion_isAbandoned_andCleanupTaskPersistedAndSent() {
        NovelVersion stale = version(VersionStatus.EMBEDDING, 3 * HOUR_MS);
        when(novelVersionRepository.findStalled(anyList(), anyLong())).thenReturn(List.of(stale));
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenAnswer(inv -> {
            CleanupTask t = inv.getArgument(0);
            t.setId(42L);
            return t;
        });

        scheduler.scan();

        assertEquals(VersionStatus.ABANDONED, stale.getStatus());
        verify(novelVersionRepository).save(stale);
        verify(cleanupTaskRepository).save(any(CleanupTask.class));
        verify(taskQueuePort).sendCleanup(any(CleanupTaskMessage.class));
    }

    @Test
    void staleSplittingVersion_isAbandoned_andCleanupSent() {
        NovelVersion stale = version(VersionStatus.SPLITTING, 3 * HOUR_MS);
        when(novelVersionRepository.findStalled(anyList(), anyLong())).thenReturn(List.of(stale));
        when(cleanupTaskRepository.save(any(CleanupTask.class))).thenAnswer(inv -> {
            CleanupTask t = inv.getArgument(0);
            t.setId(43L);
            return t;
        });

        scheduler.scan();

        assertEquals(VersionStatus.ABANDONED, stale.getStatus());
        verify(taskQueuePort).sendCleanup(any(CleanupTaskMessage.class));
    }

    @Test
    void freshEmbeddingVersion_isNotAbandoned_noCleanup() {
        NovelVersion fresh = version(VersionStatus.EMBEDDING, 10 * 60 * 1000L);
        when(novelVersionRepository.findStalled(anyList(), anyLong())).thenReturn(List.of(fresh));

        scheduler.scan();

        assertEquals(VersionStatus.EMBEDDING, fresh.getStatus());
        verify(novelVersionRepository, never()).save(any(NovelVersion.class));
        verify(cleanupTaskRepository, never()).save(any(CleanupTask.class));
        verify(taskQueuePort, never()).sendCleanup(any(CleanupTaskMessage.class));
    }

    @Test
    void versionJustUnderThreshold_isNotAbandoned() {
        // isStalled 使用严格 >；恰好等于阈值（此处取阈值下 1s）不废弃
        long now = System.currentTimeMillis();
        NovelVersion boundary = NovelVersion.builder()
                .novelId("n1").versionTag("v1")
                .status(VersionStatus.SPLITTING)
                .updatedAt(now - 2 * HOUR_MS + 1000L)
                .build();
        when(novelVersionRepository.findStalled(anyList(), anyLong())).thenReturn(List.of(boundary));

        scheduler.scan();

        assertEquals(VersionStatus.SPLITTING, boundary.getStatus());
        verify(novelVersionRepository, never()).save(any(NovelVersion.class));
        verify(taskQueuePort, never()).sendCleanup(any(CleanupTaskMessage.class));
    }
}
