package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneRepositoryJpaImplUpdateMetadataTest {

    private SceneRepository repo(JpaSceneRepository jpa, SceneMapper mapper) {
        SceneRepository r = new SceneRepositoryJpaImpl(
                jpa, mock(JpaNovelRepository.class), mock(JpaChapterRepository.class), mapper);
        ReflectionTestUtils.setField(r, "jdbcBatchSize", 500);
        return r;
    }

    @Test
    void updateScenesMetadata_writesMetadataJsonPerScene() {
        JpaSceneRepository jpa = mock(JpaSceneRepository.class);
        SceneMapper mapper = mock(SceneMapper.class);
        SceneRepository repo = repo(jpa, mapper);

        Scene s1 = Scene.builder().persistenceId(10L).metadata(new SceneMetadata()).build();
        when(mapper.metadataToJson(s1.getMetadata())).thenReturn("{\"role\":\"dialogue\"}");

        repo.updateScenesMetadata(List.of(s1));

        verify(jpa).updateMetadataJson(10L, "{\"role\":\"dialogue\"}");
    }

    @Test
    void updateScenesMetadata_skipsNullPersistenceIdOrMetadata() {
        JpaSceneRepository jpa = mock(JpaSceneRepository.class);
        SceneMapper mapper = mock(SceneMapper.class);
        SceneRepository repo = repo(jpa, mapper);

        Scene noMeta = Scene.builder().persistenceId(1L).build();
        Scene noPid = Scene.builder().metadata(new SceneMetadata()).build();

        assertDoesNotThrow(() -> repo.updateScenesMetadata(List.of(noMeta, noPid)));
        verify(jpa, never()).updateMetadataJson(Mockito.anyLong(), Mockito.anyString());
    }
}
