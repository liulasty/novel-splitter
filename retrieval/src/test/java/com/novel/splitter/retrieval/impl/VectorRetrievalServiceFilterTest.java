package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorRetrievalServiceFilterTest {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SceneRepository sceneRepository;
    private NovelRepository novelRepository;
    private VectorRetrievalService service;

    @BeforeEach
    void setUp() {
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorStore = Mockito.mock(VectorStore.class);
        sceneRepository = Mockito.mock(SceneRepository.class);
        novelRepository = Mockito.mock(NovelRepository.class);
        service = new VectorRetrievalService(embeddingService, vectorStore, sceneRepository, novelRepository);
    }

    private void stubSearch() {
        when(embeddingService.embedBatch(List.of("q"))).thenReturn(List.of(new float[]{0.1f}));
        when(vectorStore.collectionExists(anyString())).thenReturn(true);
        when(vectorStore.search(any(float[].class), anyInt(), anyMap(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    private RetrievalQuery query() {
        return RetrievalQuery.builder()
                .question("q").novelId("n1").version("v1")
                .chunkSize(478).chunkOverlap(65)
                .topK(5)
                .build();
    }

    @Test
    void retrieve_appliesRoleAndStructuredFiltersWhenEnabled() {
        ReflectionTestUtils.setField(service, "roleFilterEnabled", true);
        ReflectionTestUtils.setField(service, "structuredFilterEnabled", true);
        RetrievalQuery query = query();
        query.setRole("dialogue");
        query.setCharacterFilter("萧炎");
        query.setLocationFilter("乌坦城");
        query.setTimeFilter("夜晚");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        Map<String, Object> filter = captor.getValue();
        assertEquals("dialogue", filter.get("role"));
        assertEquals(Map.of("$contains", "萧炎"), filter.get("characters"));
        assertEquals("乌坦城", filter.get("location"));
        assertEquals("夜晚", filter.get("time"));
    }

    @Test
    void retrieve_ignoresFiltersWhenGatesDisabled() {
        RetrievalQuery query = query();
        query.setRole("dialogue");
        query.setCharacterFilter("萧炎");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        Map<String, Object> filter = captor.getValue();
        assertFalse(filter.containsKey("role"));
        assertFalse(filter.containsKey("characters"));
        assertFalse(filter.containsKey("location"));
        assertFalse(filter.containsKey("time"));
    }

    @Test
    void retrieve_rejectsUnknownRoleValue() {
        ReflectionTestUtils.setField(service, "roleFilterEnabled", true);
        RetrievalQuery query = query();
        query.setRole("garbage");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        assertFalse(captor.getValue().containsKey("role"));
    }

    @Test
    void retrieve_skipsBlankStructuredFilters() {
        ReflectionTestUtils.setField(service, "structuredFilterEnabled", true);
        RetrievalQuery query = query();
        query.setCharacterFilter("  ");
        query.setLocationFilter("");
        stubSearch();

        service.retrieve(query);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStore).search(any(float[].class), eq(5), captor.capture(), anyString());
        Map<String, Object> filter = captor.getValue();
        assertFalse(filter.containsKey("characters"));
        assertFalse(filter.containsKey("location"));
    }
}
