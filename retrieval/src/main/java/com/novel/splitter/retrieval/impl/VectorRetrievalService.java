package com.novel.splitter.retrieval.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.retrieval.dto.RetrievalQuery;
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

    private static final String META_NOVEL = "novel";
    private static final String META_VERSION = "version";
    private static final String META_PARENT_SCENE_ID = "parent_scene_id";
    private static final String KEY_SEPARATOR = "::";

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final SceneRepository sceneRepository;

    @Override
    public List<Scene> retrieve(RetrievalQuery query) {
        if (query == null || query.getQuestion() == null || query.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Question cannot be null or blank");
        }
        if (query.getTopK() < 1) {
            throw new IllegalArgumentException("TopK must be greater than or equal to 1");
        }

        log.info("Processing retrieval query: '{}' (topK={})", query.getQuestion(), query.getTopK());

        // 1. Embedding
        float[] queryVector = embeddingService.embedBatch(Collections.singletonList(query.getQuestion())).get(0);

        // 2. Vector Search
        Map<String, Object> filter = new HashMap<>();
        if (query.getNovel() != null && !query.getNovel().isBlank()) {
            filter.put(META_NOVEL, query.getNovel());
        }
        if (query.getVersion() != null && !query.getVersion().isBlank()) {
            filter.put(META_VERSION, query.getVersion());
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
            if (meta == null || !meta.containsKey(META_NOVEL) || !meta.containsKey(META_VERSION)) {
                log.warn("Vector record {} missing metadata (novel/version), skipping hydration", record.getChunkId());
                continue;
            }
            String key = meta.get(META_NOVEL) + KEY_SEPARATOR + meta.get(META_VERSION);
            groupedRecords.computeIfAbsent(key, k -> new ArrayList<>()).add(record);
            processingOrder.add(record);
        }

        // Collect all target IDs to fetch them in a single IO operation
        List<String> allTargetIds = processingOrder.stream()
                .map(this::resolveTargetId)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Scene> allScenesMap = new HashMap<>();
        if (!allTargetIds.isEmpty()) {
            List<Scene> allScenes = sceneRepository.findBySceneIds(allTargetIds);
            allScenesMap = allScenes.stream()
                    .collect(Collectors.toMap(Scene::getId, s -> s, (v1, v2) -> v1));
        }

        // Hydrate scenes and track failures
        Map<String, Scene> hydratedScenes = new HashMap<>();
        List<String> failedGroups = new ArrayList<>();

        for (Map.Entry<String, List<VectorRecord>> entry : groupedRecords.entrySet()) {
            String[] parts = entry.getKey().split(KEY_SEPARATOR, 2);
            String novel = parts[0];
            String version = parts.length > 1 ? parts[1] : "";

            try {
                for (VectorRecord r : entry.getValue()) {
                    String targetId = resolveTargetId(r);
                    Scene s = allScenesMap.get(targetId);
                    
                    if (s != null) {
                        // Deduplicate: keep the highest score for the parent scene
                        if (hydratedScenes.containsKey(targetId)) {
                            Scene existing = hydratedScenes.get(targetId);
                            if (r.getScore() > existing.getScore()) {
                                existing.setScore(r.getScore());
                            }
                        } else {
                            s.setScore(r.getScore());
                            hydratedScenes.put(targetId, s);
                        }
                    } else {
                        log.warn("Scene {} not found in file product {}/{}", targetId, novel, version);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to load scenes for {}/{}", novel, version, e);
                failedGroups.add(novel + "/" + version);
            }
        }

        if (!failedGroups.isEmpty()) {
            log.warn("Hydration failed for the following novel/version groups: {}", String.join(", ", failedGroups));
        }

        // Restore order based on vector search results, avoiding duplicates
        List<Scene> resultList = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (VectorRecord r : processingOrder) {
            String targetId = resolveTargetId(r);
            if (!seenIds.contains(targetId)) {
                Scene s = hydratedScenes.get(targetId);
                if (s != null) {
                    resultList.add(s);
                    seenIds.add(targetId);
                }
            }
        }
        
        return resultList;
    }

    /**
     * 从 VectorRecord 元数据中解析 targetId，如果存在 parent_scene_id 则返回，否则返回 chunkId
     *
     * @param record 向量记录
     * @return 目标 Scene 的 ID
     */
    private String resolveTargetId(VectorRecord record) {
        if (record.getMetadata() != null && record.getMetadata().containsKey(META_PARENT_SCENE_ID)) {
            return (String) record.getMetadata().get(META_PARENT_SCENE_ID);
        }
        return record.getChunkId();
    }
}
