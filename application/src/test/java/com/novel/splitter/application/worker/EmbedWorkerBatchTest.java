package com.novel.splitter.application.worker;

import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbedWorkerBatchTest {

    @Mock
    private EmbedNovelUseCase embedNovelUseCase;
    @Mock
    private TaskService taskService;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @InjectMocks
    private EmbedWorker embedWorker;

    @Captor
    private ArgumentCaptor<List<Long>> embedBatchIdsCaptor;

    @BeforeEach
    void setSubBatchSize() {
        ReflectionTestUtils.setField(embedWorker, "embedSubBatchSize", 3);
    }

    @Test
    void partitionsToEmbed_intoCeilNOverSubBatchSize_embedBatchCalls() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        List<Long> pids = LongStream.rangeClosed(1, 10).boxed().toList();
        List<Scene> scenes = new ArrayList<>();
        for (Long pid : pids) {
            scenes.add(Scene.builder()
                    .persistenceId(pid)
                    .id("s" + pid)
                    .text("x")
                    .embedStatus(EmbedStatus.PENDING)
                    .embedRunId("run-a")
                    .build());
        }
        when(sceneRepository.findByIds(pids)).thenReturn(scenes);

        when(embedNovelUseCase.embedBatch(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<EmbedSceneTaskMessage> batch = new ArrayList<>();
        for (Long pid : pids) {
            batch.add(new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-a", pid));
        }
        embedWorker.onEmbedSceneBatch(batch);

        verify(embedNovelUseCase, times(4)).embedBatch(embedBatchIdsCaptor.capture());
        assertThat(embedBatchIdsCaptor.getAllValues().get(0)).containsExactly(1L, 2L, 3L);
        assertThat(embedBatchIdsCaptor.getAllValues().get(1)).containsExactly(4L, 5L, 6L);
        assertThat(embedBatchIdsCaptor.getAllValues().get(2)).containsExactly(7L, 8L, 9L);
        assertThat(embedBatchIdsCaptor.getAllValues().get(3)).containsExactly(10L);
    }

    @Test
    void embedBatchReturnsEmpty_doesNotMarkSuccess_usesPerRowFailed() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        Scene emptyText = Scene.builder()
                .persistenceId(7L)
                .id("s7")
                .text("   ")
                .embedStatus(EmbedStatus.PENDING)
                .embedRunId("run-a")
                .build();
        when(sceneRepository.findByIds(List.of(7L))).thenReturn(List.of(emptyText));
        when(embedNovelUseCase.embedBatch(List.of(7L))).thenReturn(List.of());

        embedWorker.onEmbedSceneBatch(List.of(
                new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-a", 7L)));

        verify(sceneRepository, never()).batchUpdateEmbedOutcome(anyList(), eq("run-a"), eq(EmbedStatus.SUCCESS), isNull());
        verify(sceneRepository).updateEmbedOutcome(
                7L, "run-a", EmbedStatus.FAILED, "Empty or blank scene text; skipping embed.");
    }

    @Test
    void terminalTask_skipsEmbedBatch() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.SUCCESS);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        embedWorker.onEmbedSceneBatch(List.of(
                new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-a", 1L)));

        verify(embedNovelUseCase, times(0)).embedBatch(anyList());
    }

    @Test
    void successSameRun_skipped_notSentToEmbedBatch() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        Scene done = Scene.builder()
                .persistenceId(9L)
                .id("s9")
                .text("hi")
                .embedStatus(EmbedStatus.SUCCESS)
                .embedRunId("run-a")
                .build();
        when(sceneRepository.findByIds(List.of(9L))).thenReturn(List.of(done));

        embedWorker.onEmbedSceneBatch(List.of(
                new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-a", 9L)));

        verify(embedNovelUseCase, times(0)).embedBatch(anyList());
    }

    @Test
    void successButDifferentEmbedRun_stillCallsEmbedBatch() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-b");
        when(taskService.getTask("t1")).thenReturn(task);

        Scene staleSuccess = Scene.builder()
                .persistenceId(3L)
                .id("s3")
                .text("body")
                .embedStatus(EmbedStatus.SUCCESS)
                .embedRunId("run-a")
                .build();
        when(sceneRepository.findByIds(List.of(3L))).thenReturn(List.of(staleSuccess));
        when(embedNovelUseCase.embedBatch(List.of(3L))).thenReturn(List.of(3L));

        embedWorker.onEmbedSceneBatch(List.of(
                new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-b", 3L)));

        verify(embedNovelUseCase).embedBatch(List.of(3L));
        verify(sceneRepository).batchUpdateEmbedOutcome(List.of(3L), "run-b", EmbedStatus.SUCCESS, null);
    }

    @Test
    void embedBatchThrows_marksSubBatchFailed() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        Scene sc = Scene.builder()
                .persistenceId(2L)
                .id("s2")
                .text("t")
                .embedStatus(EmbedStatus.PENDING)
                .embedRunId("run-a")
                .build();
        when(sceneRepository.findByIds(List.of(2L))).thenReturn(List.of(sc));
        when(embedNovelUseCase.embedBatch(List.of(2L))).thenThrow(new RuntimeException("onnx boom"));

        embedWorker.onEmbedSceneBatch(List.of(
                new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-a", 2L)));

        verify(sceneRepository).batchUpdateEmbedOutcome(
                eq(List.of(2L)), eq("run-a"), eq(EmbedStatus.FAILED), anyString());
    }
}
