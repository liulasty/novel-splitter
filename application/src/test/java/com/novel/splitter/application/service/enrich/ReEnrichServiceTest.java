package com.novel.splitter.application.service.enrich;

import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReEnrichServiceTest {

    private SceneRepository sceneRepository;
    private NovelRepository novelRepository;
    private TaskQueuePort taskQueuePort;
    private ReEnrichService service;

    @BeforeEach
    void setUp() {
        sceneRepository = Mockito.mock(SceneRepository.class);
        novelRepository = Mockito.mock(NovelRepository.class);
        taskQueuePort = Mockito.mock(TaskQueuePort.class);
        service = new ReEnrichService(sceneRepository, novelRepository, new EnrichPublisher(taskQueuePort));
    }

    @Test
    void reEnrich_groupsScenesByChapter_publishesOneMessagePerChapter() {
        when(novelRepository.findById("novel"))
                .thenReturn(Optional.of(Novel.builder().activeVersionTag("v2").build()));
        when(sceneRepository.findAllByNovelIdAndVersion("novel", "v2")).thenReturn(List.of(
                Scene.builder().persistenceId(1L).chapterIndex(1).build(),
                Scene.builder().persistenceId(2L).chapterIndex(1).build(),
                Scene.builder().persistenceId(3L).chapterIndex(2).build()));

        service.reEnrich("novel", "v2");

        ArgumentCaptor<EnrichTaskMessage> captor = ArgumentCaptor.forClass(EnrichTaskMessage.class);
        verify(taskQueuePort, Mockito.times(2)).sendEnrich(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(EnrichTaskMessage::getSceneIds)
                .containsExactlyInAnyOrder(List.of(1L, 2L), List.of(3L));
    }

    @Test
    void reEnrich_explicitVersion_publishesEnrichWithSceneIds() {
        when(novelRepository.findById("novel"))
                .thenReturn(Optional.of(Novel.builder().activeVersionTag("v9").build()));
        Scene s1 = Scene.builder().persistenceId(10L).build();
        Scene s2 = Scene.builder().persistenceId(20L).build();
        when(sceneRepository.findAllByNovelIdAndVersion("novel", "v2")).thenReturn(List.of(s1, s2));

        service.reEnrich("novel", "v2");

        ArgumentCaptor<EnrichTaskMessage> captor = ArgumentCaptor.forClass(EnrichTaskMessage.class);
        verify(taskQueuePort).sendEnrich(captor.capture());
        assertEquals("novel", captor.getValue().getNovelId());
        assertEquals("v2", captor.getValue().getVersion());
        assertEquals(List.of(10L, 20L), captor.getValue().getSceneIds());
    }

    @Test
    void reEnrich_blankVersion_resolvesActiveVersion() {
        when(novelRepository.findById("novel"))
                .thenReturn(Optional.of(Novel.builder().activeVersionTag("v3").build()));
        when(sceneRepository.findAllByNovelIdAndVersion("novel", "v3"))
                .thenReturn(List.of(Scene.builder().persistenceId(1L).build()));

        service.reEnrich("novel", null);

        ArgumentCaptor<EnrichTaskMessage> captor = ArgumentCaptor.forClass(EnrichTaskMessage.class);
        verify(taskQueuePort).sendEnrich(captor.capture());
        assertEquals("v3", captor.getValue().getVersion());
    }

    @Test
    void reEnrich_novelNotFound_throws() {
        when(novelRepository.findById("novel")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.reEnrich("novel", null));
    }

    @Test
    void reEnrich_novelExistsButNoActiveVersion_throws() {
        when(novelRepository.findById("novel"))
                .thenReturn(Optional.of(Novel.builder().build())); // activeVersionTag 为 null
        assertThrows(IllegalArgumentException.class, () -> service.reEnrich("novel", null));
    }

    @Test
    void reEnrich_novelNotFoundWithExplicitVersion_throws() {
        when(novelRepository.findById("novel")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.reEnrich("novel", "v2"));
    }

    @Test
    void reEnrich_emptySceneList_noop() {
        when(novelRepository.findById("novel"))
                .thenReturn(Optional.of(Novel.builder().activeVersionTag("v2").build()));
        when(sceneRepository.findAllByNovelIdAndVersion("novel", "v2")).thenReturn(List.of());

        service.reEnrich("novel", "v2");

        verify(taskQueuePort, never()).sendEnrich(any());
    }
}
