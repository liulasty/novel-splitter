package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeServiceGetStatsTest {

    @Mock private NovelStorageService novelStorageService;
    @Mock private NovelService novelService;
    @Mock private ChapterService chapterService;
    @Mock private NovelCacheRepository novelCacheRepository;
    @Mock private TaskService taskService;
    @Mock private TaskQueuePort taskQueuePort;
    @Mock private DownloadService downloadService;
    @Mock private SceneRepository sceneRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private DtoMapper dtoMapper;
    @Mock private EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    @Test
    void getNovelStats_skipsDeletedNovelWithLeftoverTasks_withoutThrowing() {
        when(sceneRepository.countScenesByNovelVersionAndChunk()).thenReturn(List.of());
        when(taskService.getAllTasks()).thenReturn(List.of(
                new SplitTask("t1", TaskType.SCENE_SPLIT, "del1", "f.txt", 0, "v1")));
        when(novelService.listNovels()).thenReturn(List.of()); // 软删小说不在 listNovels（@SQLRestriction is_deleted=false）

        List<NovelStatRecordDto> stats = novelFacadeService.getNovelStats();

        assertTrue(stats.isEmpty());
    }

    @Test
    void getNovelStats_includesExistingNovelWithScenes() {
        when(sceneRepository.countScenesByNovelVersionAndChunk()).thenReturn(List.of(
                new SceneCountByProfile("n1", "v1", 512, 64, 10L)));
        when(taskService.getAllTasks()).thenReturn(List.of());
        when(novelService.listNovels()).thenReturn(List.of(
                Novel.builder().id("n1").title("正常书").build()));

        List<NovelStatRecordDto> stats = novelFacadeService.getNovelStats();

        assertEquals(1, stats.size());
        assertEquals("n1", stats.get(0).getNovelId());
        assertEquals("正常书", stats.get(0).getNovelName());
        assertEquals(10L, stats.get(0).getSceneCount());
    }
}
