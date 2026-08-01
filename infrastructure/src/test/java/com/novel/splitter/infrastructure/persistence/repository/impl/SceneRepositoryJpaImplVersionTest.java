package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SceneRepositoryJpaImplVersionTest {

    @Mock private JpaSceneRepository jpaSceneRepository;
    @Mock private JpaNovelRepository jpaNovelRepository;
    @Mock private JpaChapterRepository jpaChapterRepository;
    @Mock private SceneMapper sceneMapper;

    @InjectMocks
    private SceneRepositoryJpaImpl impl;

    @Test
    void findByNovelIdAndChapterIdAndVersion_delegatesWithVersion() {
        when(jpaSceneRepository.findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(5L), eq("v2"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.unpaged(), 0));

        PagedResult<Scene> result = impl.findByNovelIdAndChapterIdAndVersion("n1", 5L, "v2", PageQuery.of(0, 200));

        verify(jpaSceneRepository).findByNovelIdAndChapterIdAndVersion(eq("n1"), eq(5L), eq("v2"), any(Pageable.class));
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void listSplitProfilesByNovelId_preservesQueryOrder_lastIsLatest() {
        Object[] v1 = {"v1", 512, 64};
        Object[] v2 = {"v2", 1024, 128};
        when(jpaSceneRepository.findDistinctProfilesByNovelId("n1")).thenReturn(List.of(v1, v2));

        List<SceneSplitProfile> profiles = impl.listSplitProfilesByNovelId("n1");

        assertEquals(2, profiles.size());
        assertEquals("v1", profiles.get(0).version());   // record accessor
        assertEquals("v2", profiles.get(1).version());   // last = latest（查询 ORDER BY MAX(id)）
    }
}
