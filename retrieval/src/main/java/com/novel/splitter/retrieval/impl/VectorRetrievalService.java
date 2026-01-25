package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.retrieval.api.RetrievalQuery;
import com.novel.splitter.retrieval.api.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于向量的检索服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRetrievalService implements RetrievalService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;

    @Override
    public List<Scene> retrieve(RetrievalQuery query) {
        log.info("Processing retrieval query: '{}' (topK={})", query.getQuestion(), query.getTopK());

        // 1. Embedding
        float[] queryVector = embeddingService.embed(query.getQuestion());

        // 2. Vector Search
        List<VectorRecord> records = vectorStore.search(queryVector, query.getTopK());
        log.debug("Found {} vector matches", records.size());

        // 3. Hydrate (Vector -> Scene)
        return records.stream()
                .map(record -> sceneRepository.findById(record.getChunkId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
