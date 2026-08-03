package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 检索活跃指针 + 集合名解析契约测试。
 */
@ExtendWith(MockitoExtension.class)
class VectorRetrievalActiveVersionTest {

    private static final String NOVEL_ID = "n1";
    private static final String VERSION = "v1";
    private static final String COLLECTION_NAME = VectorStore.collectionNameFor(NOVEL_ID, VERSION);

    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private NovelRepository novelRepository;

    private VectorRetrievalService vectorRetrievalService;

    @BeforeEach
    void setUp() {
        vectorRetrievalService = new VectorRetrievalService(
                embeddingService, vectorStore, sceneRepository, novelRepository);
    }

    @Test
    void resolvesActiveVersionWhenNotExplicit() {
        Novel novel = Novel.builder().id(NOVEL_ID).activeVersionTag(VERSION).build();
        when(novelRepository.findById(NOVEL_ID)).thenReturn(Optional.of(novel));
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(true);
        when(vectorStore.search(any(), eq(5), any(), eq(COLLECTION_NAME)))
                .thenReturn(List.of(
                        new VectorRecord("scene-1", 0.95,
                                Map.of("novelId", NOVEL_ID, "version", VERSION, "chunkSize", 350, "chunkOverlap", 65))));
        when(sceneRepository.findBySceneIds(any())).thenReturn(List.of(
                Scene.builder().id("scene-1").text("test scene").build()));

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("test question");
        query.setTopK(5);
        query.setNovelId(NOVEL_ID);
        // version intentionally null → should be resolved from activeVersionTag

        List<Scene> results = vectorRetrievalService.retrieve(query);

        assertThat(results).hasSize(1);
        verify(vectorStore).search(any(), eq(5), any(), eq(COLLECTION_NAME));
        verify(novelRepository).findById(NOVEL_ID);
    }

    @Test
    void usesExplicitVersionWhenProvided() {
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(true);
        when(vectorStore.search(any(), eq(3), any(), eq(COLLECTION_NAME)))
                .thenReturn(Collections.emptyList());

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("test");
        query.setTopK(3);
        query.setNovelId(NOVEL_ID);
        query.setVersion(VERSION);  // explicit version

        List<Scene> results = vectorRetrievalService.retrieve(query);

        assertThat(results).isEmpty();
        verify(vectorStore).search(any(), eq(3), any(), eq(COLLECTION_NAME));
        verify(novelRepository, never()).findById(any());
    }

    @Test
    void returnsEmptyWhenCollectionMissing() {
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(false);

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("test");
        query.setTopK(3);
        query.setNovelId(NOVEL_ID);
        query.setVersion(VERSION);

        List<Scene> results = vectorRetrievalService.retrieve(query);

        assertThat(results).isEmpty();
        verify(vectorStore, never()).search(any(), anyInt(), any(), anyString());
    }

    @Test
    void returnsEmptyWhenSearchThrows() {
        when(embeddingService.embedBatch(any())).thenReturn(List.of(new float[]{0.1f, 0.2f}));
        when(vectorStore.collectionExists(COLLECTION_NAME)).thenReturn(true);
        when(vectorStore.search(any(), anyInt(), any(), eq(COLLECTION_NAME)))
                .thenThrow(new RuntimeException("Chroma unavailable"));

        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("test");
        query.setTopK(3);
        query.setNovelId(NOVEL_ID);
        query.setVersion(VERSION);

        List<Scene> results = vectorRetrievalService.retrieve(query);

        assertThat(results).isEmpty();
    }

    @Test
    void rejectsBlankQuestion() {
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("  ");
        query.setTopK(3);
        query.setNovelId(NOVEL_ID);

        assertThatThrownBy(() -> vectorRetrievalService.retrieve(query))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTopKLessThanOne() {
        RetrievalQuery query = new RetrievalQuery();
        query.setQuestion("test");
        query.setTopK(0);
        query.setNovelId(NOVEL_ID);

        assertThatThrownBy(() -> vectorRetrievalService.retrieve(query))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
