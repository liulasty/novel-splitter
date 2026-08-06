package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.infrastructure.persistence.entity.JpaSceneEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SceneRepositoryJpaImplSeqRangeTest {

    @Test
    void findByProfileAndSeqRange_delegatesToJpaAndMaps() {
        JpaSceneRepository jpaSceneRepository = Mockito.mock(JpaSceneRepository.class);
        SceneMapper mapper = Mockito.mock(SceneMapper.class);
        SceneRepository repo = new SceneRepositoryJpaImpl(
                jpaSceneRepository,
                Mockito.mock(JpaNovelRepository.class),
                Mockito.mock(JpaChapterRepository.class),
                mapper);
        ReflectionTestUtils.setField(repo, "jdbcBatchSize", 500);

        JpaSceneEntity e1 = new JpaSceneEntity();
        e1.setSeq(10L);
        Scene mapped = Scene.builder().id("s10").seq(10L).build();
        when(mapper.toDomain(e1)).thenReturn(mapped);
        when(jpaSceneRepository.findByNovelIdAndVersionAndChunkSizeAndChunkOverlapAndSeqBetween(
                "novel", "v1", 478, 65, 9L, 11L)).thenReturn(List.of(e1));

        List<Scene> result = repo.findByProfileAndSeqRange("novel", "v1", 478, 65, 9, 11);

        assertEquals(1, result.size());
        assertEquals("s10", result.get(0).getId());
        verify(jpaSceneRepository).findByNovelIdAndVersionAndChunkSizeAndChunkOverlapAndSeqBetween(
                "novel", "v1", 478, 65, 9L, 11L);
    }
}
