package com.novel.splitter.application.worker;

import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.orchestration.EmbedRunDbCoordinator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EmbedSceneTaskMessage;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EmbedWorker / EmbedPipelineOrchestrator 批次游标 + 版本状态联动契约（Mockito）：
 * <ul>
 *   <li>resume 结合 NovelVersion.embedCursorSceneSeq，只对游标之后的 PENDING/FAILED 补投；</li>
 *   <li>全量 scene SUCCESS 后版本置 EMBED_DONE。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class EmbedWorkerCursorTest {

    @Mock
    private EmbedNovelUseCase embedNovelUseCase;
    @Mock
    private TaskService taskService;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private EmbedPipelineOrchestrator embedPipelineOrchestrator;
    @Mock
    private NovelVersionRepository novelVersionRepository;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private TaskQueuePort taskQueuePort;
    @Mock
    private EmbedRunDbCoordinator embedRunDbCoordinator;

    @Test
    void completedBatchesAreSkippedOnResume() {
        EmbedPipelineOrchestrator orchestrator = new EmbedPipelineOrchestrator(
                taskService, sceneRepository, vectorStore, taskQueuePort, embedRunDbCoordinator, novelVersionRepository);
        ReflectionTestUtils.setField(orchestrator, "scenePublishBatchSize", 200);

        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-1");
        task.setNovelId("n1");
        task.setVersion("v1");
        when(taskService.getTask("t1")).thenReturn(task);
        when(sceneRepository.resolveChunkProfileForEmbedRun("n1", "v1", "run-1"))
                .thenReturn(Optional.of(new int[]{350, 65}));
        when(novelVersionRepository.findById("n1", "v1"))
                .thenReturn(Optional.of(NovelVersion.builder()
                        .novelId("n1").versionTag("v1")
                        .status(VersionStatus.EMBEDDING)
                        .embedCursorSceneSeq(200L)
                        .build()));

        List<Long> allIds = LongStream.rangeClosed(1, 500).boxed().toList();
        when(sceneRepository.listPersistenceIdsForEmbedResume("n1", "v1", 350, 65, "run-1")).thenReturn(allIds);
        List<Scene> scenes = new ArrayList<>();
        for (Long pid : allIds) {
            scenes.add(Scene.builder().persistenceId(pid).id("s" + pid).seq(pid).build());
        }
        when(sceneRepository.findByIds(anyList())).thenReturn(scenes);

        orchestrator.resumeEmbedRun("t1");

        ArgumentCaptor<List<EmbedSceneTaskMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskQueuePort, atLeastOnce()).sendEmbedScenes(captor.capture());
        List<Long> published = new ArrayList<>();
        for (List<EmbedSceneTaskMessage> batch : captor.getAllValues()) {
            for (EmbedSceneTaskMessage m : batch) {
                published.add(m.getScenePersistenceId());
            }
        }
        assertThat(published).hasSize(300);
        assertThat(published).allSatisfy(pid -> assertThat(pid).isGreaterThan(200));
        assertThat(published).doesNotContain(1L, 100L, 200L);
    }

    @Test
    void allEmbeddedMarksVersionEmbedDone() {
        EmbedWorker worker = new EmbedWorker(
                embedNovelUseCase, taskService, sceneRepository, embedPipelineOrchestrator, novelVersionRepository);
        ReflectionTestUtils.setField(worker, "embedSubBatchSize", 4);

        SplitTask task = new SplitTask();
        task.setTaskId("t1");
        task.setStatus(SplitTask.TaskStatus.PROCESSING);
        task.setCurrentEmbedRunId("run-a");
        when(taskService.getTask("t1")).thenReturn(task);

        NovelVersion version = NovelVersion.builder()
                .novelId("n1").versionTag("v1")
                .status(VersionStatus.EMBEDDING)
                .embedCursorSceneSeq(0L)
                .build();
        when(novelVersionRepository.findById("n1", "v1")).thenReturn(Optional.of(version));

        Scene scene = Scene.builder()
                .persistenceId(1L).id("s1").seq(1L).text("body")
                .embedStatus(EmbedStatus.PENDING).embedRunId("run-a")
                .build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(scene));
        when(embedNovelUseCase.embedBatch(eq(List.of(1L)), anyString())).thenReturn(List.of(1L));
        when(sceneRepository.countEmbedByRunAndStatus("n1", "v1", 350, 65, "run-a", EmbedStatus.SUCCESS)).thenReturn(1L);
        when(sceneRepository.countByProfile("n1", "v1", 350, 65)).thenReturn(1L);

        worker.onEmbedSceneBatch(List.of(
                new EmbedSceneTaskMessage("t1", "n1", "v1", 350, 65, "run-a", 1L)));

        ArgumentCaptor<NovelVersion> captor = ArgumentCaptor.forClass(NovelVersion.class);
        verify(novelVersionRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(NovelVersion::getStatus)
                .contains(VersionStatus.EMBED_DONE);
        assertThat(version.getEmbedCursorSceneSeq()).isEqualTo(1L);
    }
}
