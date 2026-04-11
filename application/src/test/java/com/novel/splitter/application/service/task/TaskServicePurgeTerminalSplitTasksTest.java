package com.novel.splitter.application.service.task;

import com.novel.splitter.application.port.out.TaskCachePort;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.repository.SplitTaskRepository;
import com.novel.splitter.domain.repository.TaskEventRepository;
import com.novel.splitter.domain.task.SplitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServicePurgeTerminalSplitTasksTest {

    @Mock
    private SplitTaskRepository taskRepository;
    @Mock
    private TaskEventRepository taskEventRepository;
    @Mock
    private TaskCachePort taskCachePort;
    @Mock
    private NovelRepository novelRepository;
    @Mock
    private SceneRepository sceneRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void purgeForNovel_deletesEventsTasksAndEvictsCache() {
        when(taskRepository.findTaskIdsByNovelIdAndStatuses("n1", List.of(SplitTask.TaskStatus.SUCCESS, SplitTask.TaskStatus.FAILED)))
                .thenReturn(List.of("a", "b"));

        int n = taskService.purgeTerminalSplitTasksForNovel("n1");

        assertThat(n).isEqualTo(2);
        verify(taskEventRepository).deleteByTaskIds(List.of("a", "b"));
        verify(taskRepository).deleteAllByIds(List.of("a", "b"));
        verify(taskCachePort).evict("a");
        verify(taskCachePort).evict("b");
    }

    @Test
    void purgeForNovel_blankId_noop() {
        assertThat(taskService.purgeTerminalSplitTasksForNovel("  ")).isZero();
        verify(taskRepository, never()).findTaskIdsByNovelIdAndStatuses(anyString(), anyList());
    }

    @Test
    void purgeForVersion_delegatesToVersionQuery() {
        when(taskRepository.findTaskIdsByNovelIdAndVersionAndStatuses(
                "n1", "v1", List.of(SplitTask.TaskStatus.SUCCESS, SplitTask.TaskStatus.FAILED)))
                .thenReturn(List.of("t1"));

        assertThat(taskService.purgeTerminalSplitTasksForNovelAndVersion("n1", "v1")).isEqualTo(1);
        verify(taskEventRepository).deleteByTaskIds(List.of("t1"));
        verify(taskRepository).deleteAllByIds(List.of("t1"));
        verify(taskCachePort).evict("t1");
    }
}
