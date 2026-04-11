package com.novel.splitter.application.service.chroma;

import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.embedding.store.ChromaVectorStore;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.application.model.dto.ChromaVersionDiagnosticDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChromaAdminServiceImpl implements ChromaAdminService {

    private final VectorStore vectorStore;
    private final ChromaApiClient chromaApiClient;
    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;
    private final ObjectMapper objectMapper;

    @Value("${chroma.collection:novel-splitter}")
    private String collectionName;

    @Value("${chroma.hnsw-space:cosine}")
    private String chromaHnswSpace;

    @Override
    public StreamingResponseBody exportData(String novelName, String version, Integer chunkSize, Integer chunkOverlap) {
        return outputStream -> {
            try (JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(outputStream)) {
                jsonGenerator.writeStartArray();
                // Simple implementation fetching lists, could be optimized via streaming in Repository
                if (novelName == null || novelName.isBlank()) {
                    throw new IllegalArgumentException("novelName must not be blank");
                }
                String novelId = resolveNovelId(novelName);
                List<com.novel.splitter.domain.model.Scene> scenes;
                if (version != null && !version.isBlank()
                        && chunkSize != null && chunkOverlap != null) {
                    scenes = sceneRepository.findByProfile(novelId, version, chunkSize, chunkOverlap);
                } else if (version != null && !version.isBlank()) {
                    scenes = sceneRepository.findAllByNovelIdAndVersion(novelId, version);
                } else {
                    scenes = sceneRepository.findAllByNovelId(novelId);
                }
                
                for (com.novel.splitter.domain.model.Scene scene : scenes) {
                    try {
                        objectMapper.writeValue(jsonGenerator, scene);
                    } catch (IOException e) {
                        throw new RuntimeException("Error writing JSON for entity", e);
                    }
                }
                jsonGenerator.writeEndArray();
            }
        };
    }

    @Override
    public Map<String, Object> getStats() {
        long count = vectorStore.count();
        return Map.of(
                "count", count,
                "storeType", vectorStore.getClass().getSimpleName()
        );
    }

    @Override
    public Map<String, String> reset() {
        vectorStore.reset();
        return Map.of("message", "Database reset successfully");
    }

    @Override
    public Map<String, String> delete(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            throw new IllegalArgumentException("Filter cannot be empty");
        }
        vectorStore.delete(filter);
        return Map.of("message", "Documents deleted successfully");
    }

    @Override
    public Map<String, Object> healthcheck() {
        return chromaApiClient.getMap("/api/v2/healthcheck");
    }

    @Override
    public Map<String, String> version() {
        String version = chromaApiClient.getString("/api/v2/version");
        return Map.of("version", version != null ? version.replace("\"", "") : "unknown");
    }

    @Override
    public Map<String, Object> heartbeat() {
        return chromaApiClient.getMap("/api/v2/heartbeat");
    }

    @Override
    public Map<String, String> rebuildCollection() {
        String pathPrefix = "/api/v2/tenants/default_tenant/databases/default_database/collections";

        try {
            chromaApiClient.delete(pathPrefix + "/" + collectionName);
            log.info("Deleted ChromaDB collection: {}", collectionName);
        } catch (Exception e) {
            log.warn("Failed to delete collection (might not exist): {}", e.getMessage());
        }

        String space = chromaHnswSpace != null && !chromaHnswSpace.isBlank()
                ? chromaHnswSpace.trim().toLowerCase()
                : "cosine";
        Map<String, Object> body = Map.of(
                "name", collectionName,
                "metadata", Map.of(ChromaVectorStore.CHROMA_HNSW_SPACE_KEY, space)
        );
        
        try {
            chromaApiClient.post(pathPrefix, body);
            log.info("Created ChromaDB collection: {} with hnsw:space={}", collectionName, space);
        } catch (Exception e) {
            log.error("Failed to create collection: {}", e.getMessage());
            throw new RuntimeException("Failed to recreate collection", e);
        }

        sceneRepository.deleteAll();
        log.info("Cleared local DB scenes via collection rebuild logic");

        return Map.of("message", "Collection rebuilt successfully");
    }

    @Override
    public ChromaVersionDiagnosticDto getVersionDiagnostics(String novel, String version, Integer chunkSize, Integer chunkOverlap) {
        String novelId = resolveNovelId(novel);
        long dbCount = (chunkSize != null && chunkOverlap != null)
                ? sceneRepository.countByProfile(novelId, version, chunkSize, chunkOverlap)
                : sceneRepository.countAllByNovelIdAndVersion(novelId, version);
        long chromaCount = 0;
        List<String> metadataKeys = new ArrayList<>();

        try {
            // Find collection id
            Object collectionsObj = proxyGet("/api/v2/tenants/default_tenant/databases/default_database/collections");
            String collectionId = null;
            if (collectionsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        if (collectionName.equals(map.get("name"))) {
                            collectionId = (String) map.get("id");
                            break;
                        }
                    }
                }
            }

            if (collectionId != null) {
                List<Map<String, Object>> andClauses = new ArrayList<>();
                andClauses.add(Map.of("novelId", Map.of("$eq", novelId)));
                andClauses.add(Map.of("version", Map.of("$eq", version)));
                if (chunkSize != null && chunkOverlap != null) {
                    andClauses.add(Map.of("chunkSize", Map.of("$eq", chunkSize)));
                    andClauses.add(Map.of("chunkOverlap", Map.of("$eq", chunkOverlap)));
                }
                Map<String, Object> where = Map.of("$and", andClauses);

                // Get count
                Map<String, Object> countBody = Map.of(
                        "where", where,
                        "include", List.of()
                );
                Object countRes = proxyPost("/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId + "/get", countBody);
                if (countRes instanceof Map<?, ?> map && map.get("ids") instanceof List<?> ids) {
                    chromaCount = ids.size();
                }

                // Get 1 record metadata
                Map<String, Object> metaBody = Map.of(
                        "where", where,
                        "limit", 1,
                        "include", List.of("metadatas")
                );
                Object metaRes = proxyPost("/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId + "/get", metaBody);
                if (metaRes instanceof Map<?, ?> map && map.get("metadatas") instanceof List<?> metadatas) {
                    if (!metadatas.isEmpty()) {
                        Object firstMeta = metadatas.get(0);
                        if (firstMeta instanceof Map<?, ?> firstMetaMap) {
                            for (Object key : firstMetaMap.keySet()) {
                                metadataKeys.add(String.valueOf(key));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get chroma diagnostics for novel={}, version={}", novel, version, e);
            // Optionally, we could set chromaCount = -1 to indicate error
        }

        ChromaVersionDiagnosticDto dto = new ChromaVersionDiagnosticDto();
        dto.setDbCount(dbCount);
        dto.setChromaCount(chromaCount);
        dto.setConsistent(dbCount == chromaCount);
        dto.setMetadataKeys(metadataKeys);

        return dto;
    }

    private String resolveNovelId(String novelOrTitle) {
        if (novelOrTitle == null || novelOrTitle.isBlank()) {
            throw new IllegalArgumentException("novel must not be blank");
        }
        String normalized = novelOrTitle.trim();
        return novelRepository.findById(normalized)
                .map(n -> n.getId())
                .orElseGet(() -> novelRepository.findByTitle(normalized)
                        .map(n -> n.getId())
                        .orElseThrow(() -> new IllegalArgumentException("novel not found: " + normalized)));
    }

    @Override
    public Object proxyGet(String path) {
        return extractBody(chromaApiClient.get(path));
    }

    @Override
    public Object proxyPost(String path, Object body) {
        return extractBody(chromaApiClient.post(path, body));
    }

    @Override
    public Object proxyPut(String path, Object body) {
        return extractBody(chromaApiClient.put(path, body));
    }

    @Override
    public Object proxyPatch(String path, Object body) {
        return extractBody(chromaApiClient.patch(path, body));
    }

    @Override
    public Object proxyDelete(String path) {
        return extractBody(chromaApiClient.delete(path));
    }

    private Object extractBody(ResponseEntity<?> responseEntity) {
        if (responseEntity.getStatusCode().isError()) {
            throw new RuntimeException("Proxy request failed with status " + responseEntity.getStatusCode());
        }
        return responseEntity.getBody();
    }
}
