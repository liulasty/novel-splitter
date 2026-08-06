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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        service = new ReEnrichService(sceneRepository, novelRepository, taskQueuePort);
    }

    @Test
    void reEnrich_explicitVersion_publishesEnrichWithSceneIds() {
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
    void reEnrich_noVersionAndNoActive_throws() {
        when(novelRepository.findById("novel")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.reEnrich("novel", null));
    }
}
