package com.novel.splitter.application.worker;

import com.novel.splitter.application.model.dto.SceneExtractionDto;
import com.novel.splitter.application.service.enrich.SceneSemanticExtractor;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnrichWorkerTest {

    private SceneRepository repo;
    private SceneSemanticExtractor extractor;
    private EnrichWorker worker;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SceneRepository.class);
        extractor = Mockito.mock(SceneSemanticExtractor.class);
        worker = new EnrichWorker(repo, extractor);
    }

    @Test
    void processEnrichTask_appliesExtractionAndSaves() {
        Scene s1 = Scene.builder().persistenceId(1L).id("s1").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        Scene s2 = Scene.builder().persistenceId(2L).id("s2").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        when(repo.findByIds(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        when(extractor.extract(List.of(s1, s2)))
                .thenReturn(List.of(new SceneExtractionDto("s1", List.of("萧炎"), "乌坦城", null, "narration")));

        worker.processEnrichTask(new EnrichTaskMessage("parent", "novel", "v1", List.of(1L, 2L)));

        assertEquals(List.of("萧炎"), s1.getMetadata().getCharacters());
        assertEquals("narration", s1.getMetadata().getRole());
        assertNull(s2.getMetadata().getCharacters());
        verify(repo).updateScenesMetadata(List.of(s1));
    }

    @Test
    void processEnrichTask_chapterFailureContinuesNextChapter() {
        Scene s1 = Scene.builder().persistenceId(1L).id("s1").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        Scene s2 = Scene.builder().persistenceId(2L).id("s2").chapterIndex(2)
                .metadata(new SceneMetadata()).build();
        when(repo.findByIds(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        when(extractor.extract(List.of(s1))).thenThrow(new RuntimeException("LLM 挂了"));
        when(extractor.extract(List.of(s2)))
                .thenReturn(List.of(new SceneExtractionDto("s2", List.of("药老"), null, null, "dialogue")));

        worker.processEnrichTask(new EnrichTaskMessage("p", "novel", "v1", List.of(1L, 2L)));

        assertEquals("dialogue", s2.getMetadata().getRole());
        assertNull(s1.getMetadata().getRole());
        verify(repo).updateScenesMetadata(List.of(s2));
    }

    @Test
    void processEnrichTask_emptySceneIds_ignored() {
        worker.processEnrichTask(new EnrichTaskMessage("p", "novel", "v1", List.of()));
        verifyNoInteractions(repo);
    }

    @Test
    void processEnrichTask_chapterReturnsEmpty_stillWritesOtherChapters() {
        Scene s1 = Scene.builder().persistenceId(1L).id("s1").chapterIndex(1)
                .metadata(new SceneMetadata()).build();
        Scene s2 = Scene.builder().persistenceId(2L).id("s2").chapterIndex(2)
                .metadata(new SceneMetadata()).build();
        when(repo.findByIds(List.of(1L, 2L))).thenReturn(List.of(s1, s2));
        when(extractor.extract(List.of(s1))).thenReturn(List.of()); // 章节1 返回空（真实降级路径）
        when(extractor.extract(List.of(s2)))
                .thenReturn(List.of(new SceneExtractionDto("s2", List.of("药老"), null, null, "dialogue")));

        worker.processEnrichTask(new EnrichTaskMessage("p", "novel", "v1", List.of(1L, 2L)));

        assertNull(s1.getMetadata().getRole());
        assertEquals("dialogue", s2.getMetadata().getRole());
        verify(repo).updateScenesMetadata(List.of(s2));
    }

    @Test
    void processEnrichTask_rerunPreservesNullFields() {
        SceneMetadata meta = SceneMetadata.builder().role("dialogue").location("乌坦城").build();
        Scene s1 = Scene.builder().persistenceId(1L).id("s1").chapterIndex(1).metadata(meta).build();
        when(repo.findByIds(List.of(1L))).thenReturn(List.of(s1));
        // 二次抽取：role/location/time 为 null（未识别），characters 为空数组
        when(extractor.extract(List.of(s1)))
                .thenReturn(List.of(new SceneExtractionDto("s1", List.of(), null, null, null)));

        worker.processEnrichTask(new EnrichTaskMessage("p", "novel", "v1", List.of(1L)));

        assertEquals(List.of(), s1.getMetadata().getCharacters()); // 空数组清空
        assertEquals("dialogue", s1.getMetadata().getRole());       // null 保留旧值
        assertEquals("乌坦城", s1.getMetadata().getLocation());      // null 保留旧值
        verify(repo).updateScenesMetadata(List.of(s1));
    }
}
