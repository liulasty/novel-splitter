package com.novel.splitter.application.worker;

import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbedWorkerStaleRunTest {

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

    @Test
    void embedScene_skipsEmbedding_whenEmbedRunIdDoesNotMatchTask() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-current");
        when(taskService.getTask("t1")).thenReturn(task);

        EmbedSceneTaskMessage msg = new EmbedSceneTaskMessage(
                "t1", "n1", "v1", 350, 65, "run-stale", 99L);

        embedWorker.onEmbedSceneBatch(List.of(msg));

        verify(embedNovelUseCase, never()).embedBatch(anyList());
        verify(sceneRepository, never()).batchUpdateEmbedOutcome(anyList(), any(), any(), any());
        verify(sceneRepository, never()).updateEmbedOutcome(any(), any(), any(), any());
    }

    @Test
    void embedScene_marksSuccess_afterEmbedBatch() {
        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        Scene sc = Scene.builder().persistenceId(5L).id("sid").text("hello").embedStatus(EmbedStatus.PENDING).embedRunId("run-a").build();
        when(sceneRepository.findByIds(List.of(5L))).thenReturn(List.of(sc));
        when(embedNovelUseCase.embedBatch(eq(List.of(5L)), anyString())).thenReturn(List.of(5L));

        EmbedSceneTaskMessage msg = new EmbedSceneTaskMessage(
                "t1", "n1", "v1", 350, 65, "run-a", 5L);

        embedWorker.onEmbedSceneBatch(List.of(msg));

        verify(embedNovelUseCase).embedBatch(eq(List.of(5L)), anyString());
        verify(sceneRepository).batchUpdateEmbedOutcome(eq(List.of(5L)), eq("run-a"), eq(EmbedStatus.SUCCESS), isNull());
    }
}
