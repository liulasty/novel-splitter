package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NovelFacadeServiceSceneReadTest {

    @Mock private NovelStorageService novelStorageService;
    @Mock private NovelService novelService;
    @Mock private ChapterService chapterService;
    @Mock private NovelCacheRepository novelCacheRepository;
    @Mock private TaskService taskService;
    @Mock private TaskQueuePort taskQueuePort;
    @Mock private SceneRepository sceneRepository;
    @Mock private ChapterRepository chapterRepository;
    @Mock private DtoMapper dtoMapper;
    @Mock private EmbedPipelineOrchestrator embedPipelineOrchestrator;

    @InjectMocks
    private NovelFacadeServiceImpl novelFacadeService;

    private Novel parsedNovel() {
        return Novel.builder().id("n1").status(NovelStatus.PARSED).build();
    }

    @Test
    void getScenesByChapter_filtersByVersion_whenProvided() {
        when(novelService.getNovelById("n1")).thenReturn(parsedNovel());
        when(sceneRepository.findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(1L), eq("v2"), any(PageQuery.class)))
                .thenReturn(PagedResult.of(List.of(), 0, 200, 0));

        novelFacadeService.getScenesByChapter("n1", 1L, "v2", 0, 200);

        verify(sceneRepository).findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(1L), eq("v2"), any(PageQuery.class));
        verify(sceneRepository, never()).findByNovelIdAndChapterId(any(), any(), any());
    }

    @Test
    void getScenesByChapter_usesUnfiltered_whenVersionBlank() {
        when(novelService.getNovelById("n1")).thenReturn(parsedNovel());
        when(sceneRepository.findByNovelIdAndChapterId(eq("n1"), eq(1L), any(PageQuery.class)))
                .thenReturn(PagedResult.of(List.of(), 0, 200, 0));

        novelFacadeService.getScenesByChapter("n1", 1L, "  ", 0, 200);

        verify(sceneRepository).findByNovelIdAndChapterId(eq("n1"), eq(1L), any(PageQuery.class));
        verify(sceneRepository, never()).findByNovelIdAndChapterIdAndVersion(any(), any(), any(), any());
    }

    @Test
    void getChapters_allowed_duringSplitting() {
        when(novelService.getNovelById("n1"))
                .thenReturn(Novel.builder().id("n1").status(NovelStatus.SPLITTING).build());
        when(chapterService.getChaptersByNovelId("n1")).thenReturn(List.of());
        when(dtoMapper.toChapterDtos(List.of())).thenReturn(List.of());

        List<ChapterDto> result = novelFacadeService.getChapters("n1");

        assertTrue(result.isEmpty());
        verify(chapterService).getChaptersByNovelId("n1");
    }

    @Test
    void getChapters_returnsEmpty_whenPending() {
        when(novelService.getNovelById("n1"))
                .thenReturn(Novel.builder().id("n1").status(NovelStatus.PENDING).build());
        when(chapterService.getChaptersByNovelId("n1")).thenReturn(List.of());
        when(dtoMapper.toChapterDtos(List.of())).thenReturn(List.of());

        List<ChapterDto> result = novelFacadeService.getChapters("n1");

        assertTrue(result.isEmpty());
        verify(chapterService).getChaptersByNovelId("n1");
    }
}
