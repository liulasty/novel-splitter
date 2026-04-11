package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.NovelPipelineRequestDto;
import com.novel.splitter.application.model.dto.SceneSplitRequestDto;
import com.novel.splitter.application.model.dto.SplitRetryRequestDto;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTaskMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeSceneSplitEmbeddingGateTest {

    @Mock
    private NovelStorageService novelStorageService;
    @Mock
    private NovelService novelService;
    @Mock
    private ChapterService chapterService;
    @Mock
    private NovelCacheRepository novelCacheRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskQueuePort taskQueuePort;
    @Mock
    private DownloadService downloadService;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private DtoMapper dtoMapper;
    @Mock
    private EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    @Test
    void sceneSplit_returns409_whenNovelEmbedding() throws IOException {
        stubStructuredArtifactsReady("n1");
        Novel embedding = Novel.builder().id("n1").status(NovelStatus.EMBEDDING).build();
        when(novelService.getNovelById("n1")).thenReturn(embedding);

        SceneSplitRequestDto req = new SceneSplitRequestDto();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> novelFacadeService.sceneSplit("n1", req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(taskQueuePort, never()).sendSplit(any(SplitTaskMessage.class));
    }

    @Test
    void retrySplit_returns409_whenNovelEmbedding() throws IOException {
        stubStructuredArtifactsReady("n1");
        Novel embedding = Novel.builder().id("n1").status(NovelStatus.EMBEDDING).build();
        when(novelService.getNovelById("n1")).thenReturn(embedding);

        SplitRetryRequestDto req = new SplitRetryRequestDto();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> novelFacadeService.retrySplit("n1", req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(taskQueuePort, never()).sendSplit(any(SplitTaskMessage.class));
    }

    @Test
    void pipelineSceneOnly_returns409_whenNovelEmbedding() throws IOException {
        stubStructuredArtifactsReady("n1");
        Novel embedding = Novel.builder().id("n1").status(NovelStatus.EMBEDDING).build();
        when(novelService.getNovelById("n1")).thenReturn(embedding);

        NovelPipelineRequestDto req = new NovelPipelineRequestDto();
        req.setStages(List.of("SPLIT"));
        req.setSplitEntry("SCENE_ONLY");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> novelFacadeService.pipeline("n1", req));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(taskQueuePort, never()).sendSplit(any(SplitTaskMessage.class));
    }

    @Test
    void sceneSplit_dispatches_whenNotEmbedding() throws IOException {
        stubStructuredArtifactsReady("n1");
        Novel ok = Novel.builder().id("n1").status(NovelStatus.SPLIT_COMPLETED).build();
        when(novelService.getNovelById("n1")).thenReturn(ok);

        SceneSplitRequestDto req = new SceneSplitRequestDto();
        novelFacadeService.sceneSplit("n1", req);

        verify(taskQueuePort).sendSplit(any(SplitTaskMessage.class));
    }

    private void stubStructuredArtifactsReady(String novelId) throws IOException {
        when(chapterService.hasChapters(novelId)).thenReturn(true);
        when(novelCacheRepository.listChapterFiles(novelId)).thenReturn(Stream.of(Path.of("chapter-1.json")));
    }
}
