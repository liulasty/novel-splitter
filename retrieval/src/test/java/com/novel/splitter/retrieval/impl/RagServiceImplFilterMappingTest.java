package com.novel.splitter.retrieval.impl;

import com.novel.splitter.retrieval.api.AnswerPolicyClassifier;
import com.novel.splitter.retrieval.api.RetrievalService;
import com.novel.splitter.retrieval.config.RagProperties;
import com.novel.splitter.retrieval.dto.RagRequest;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagServiceImplFilterMappingTest {

    private RetrievalService retrievalService;
    private RetrievalQueryBuilder queryBuilder;
    private RagServiceImpl service;

    @BeforeEach
    void setUp() {
        retrievalService = Mockito.mock(RetrievalService.class);
        queryBuilder = Mockito.mock(RetrievalQueryBuilder.class);
        service = new RagServiceImpl(retrievalService,
                Mockito.mock(RagProperties.class),
                Mockito.mock(AnswerPolicyClassifier.class),
                queryBuilder);
        when(queryBuilder.build(anyString(), anyInt())).thenReturn(RetrievalQuery.builder().build());
    }

    @Test
    void retrieve_mapsStructuredFiltersOntoQuery() {
        RagRequest request = new RagRequest();
        request.setQuestion("q");
        request.setTopK(5);
        request.setNovelId("n1");
        request.setVersion("v1");
        request.setCharacterFilter("萧炎");
        request.setLocationFilter("乌坦城");
        request.setTimeFilter("夜晚");

        service.retrieve(request);

        ArgumentCaptor<RetrievalQuery> captor = ArgumentCaptor.forClass(RetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        RetrievalQuery query = captor.getValue();
        assertEquals("萧炎", query.getCharacterFilter());
        assertEquals("乌坦城", query.getLocationFilter());
        assertEquals("夜晚", query.getTimeFilter());
    }

    @Test
    void retrieve_keepsNullFiltersAsNull() {
        RagRequest request = new RagRequest();
        request.setQuestion("q");
        request.setTopK(5);

        service.retrieve(request);

        ArgumentCaptor<RetrievalQuery> captor = ArgumentCaptor.forClass(RetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertEquals(null, captor.getValue().getCharacterFilter());
        assertEquals(null, captor.getValue().getLocationFilter());
        assertEquals(null, captor.getValue().getTimeFilter());
    }
}
