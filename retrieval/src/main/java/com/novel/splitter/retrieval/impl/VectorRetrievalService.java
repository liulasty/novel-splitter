package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.dto.RetrievalQuery;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.retrieval.api.RetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

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
        Map<String, Object> filter = new HashMap<>();
        if (query.getNovel() != null && !query.getNovel().isBlank()) {
            filter.put("novel", query.getNovel());
        }
        if (query.getVersion() != null && !query.getVersion().isBlank()) {
            filter.put("version", query.getVersion());
        }

        log.info("Executing vector search with filter: {}", filter);

        List<VectorRecord> records = vectorStore.search(queryVector, query.getTopK(), filter);
        log.debug("Found {} vector matches", records.size());

        // 3. Hydrate (Vector -> Scene)
        // Group by novel/version to minimize file IO
        Map<String, List<VectorRecord>> groupedRecords = new HashMap<>();
        List<VectorRecord> processingOrder = new ArrayList<>();

        for (VectorRecord record : records) {
            Map<String, Object> meta = record.getMetadata();
            if (meta == null || !meta.containsKey("novel") || !meta.containsKey("version")) {
                log.warn("Vector record {} missing metadata (novel/version), skipping hydration", record.getChunkId());
                continue;
            }
            String key = meta.get("novel") + "::" + meta.get("version");
            groupedRecords.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            processingOrder.add(record);
        }

        // Load scenes batch by batch
        Map<String, Scene> hydratedScenes = new HashMap<>();
        for (Map.Entry<String, List<VectorRecord>> entry : groupedRecords.entrySet()) {
            String[] parts = entry.getKey().split("::");
            String novel = parts[0];
            String version = parts[1];

            try {
                List<Scene> scenes = sceneRepository.loadScenes(novel, version);
                Map<String, Scene> sceneMap = scenes.stream()
                        .collect(Collectors.toMap(Scene::getId, s -> s, (v1, v2) -> v1));
                
                for (VectorRecord r : entry.getValue()) {
                    Scene s = sceneMap.get(r.getChunkId());
                    if (s != null) {
                        s.setScore(r.getScore());
                        hydratedScenes.put(r.getChunkId(), s);
                    } else {
                        log.warn("Scene {} not found in file product {}/{}", r.getChunkId(), novel, version);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load scenes for {}/{}", novel, version, e);
            }
        }

        // Restore order based on vector search results
        return processingOrder.stream()
                .map(r -> hydratedScenes.get(r.getChunkId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
