package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbedNovelUseCaseTest {

    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private SceneRepository sceneRepository;
    private EmbedNovelUseCase useCase;

    @BeforeEach
    void setUp() {
        embeddingService = Mockito.mock(EmbeddingService.class);
        vectorStore = Mockito.mock(VectorStore.class);
        sceneRepository = Mockito.mock(SceneRepository.class);
        useCase = new EmbedNovelUseCase(embeddingService, vectorStore, sceneRepository);
    }

    @Test
    void embedBatch_prefixContextEnabled_prependsPrefix() {
        ReflectionTestUtils.setField(useCase, "usePrefixContext", true);
        Scene s = Scene.builder().persistenceId(1L).id("s1").text("正文").prefixContext("上文").build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(s));
        List<float[]> embeddings = List.of(new float[]{1f});
        when(embeddingService.embedBatch(List.of("上文\n正文"))).thenReturn(embeddings);

        useCase.embedBatch(List.of(1L));

        verify(embeddingService).embedBatch(List.of("上文\n正文"));
        verify(vectorStore).saveBatch(List.of(s), embeddings);
    }

    @Test
    void embedBatch_flagDisabled_plainText() {
        ReflectionTestUtils.setField(useCase, "usePrefixContext", false);
        Scene s = Scene.builder().persistenceId(1L).id("s1").text("正文").prefixContext("上文").build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(s));
        when(embeddingService.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{1f}));

        useCase.embedBatch(List.of(1L));

        verify(embeddingService).embedBatch(List.of("正文"));
    }

    @Test
    void embedBatch_prefixContextBlank_fallsBackToText() {
        ReflectionTestUtils.setField(useCase, "usePrefixContext", true);
        Scene s = Scene.builder().persistenceId(1L).id("s1").text("正文").prefixContext("  ").build();
        when(sceneRepository.findByIds(List.of(1L))).thenReturn(List.of(s));
        when(embeddingService.embedBatch(List.of("正文"))).thenReturn(List.of(new float[]{1f}));

        useCase.embedBatch(List.of(1L));

        verify(embeddingService).embedBatch(List.of("正文"));
    }
}
