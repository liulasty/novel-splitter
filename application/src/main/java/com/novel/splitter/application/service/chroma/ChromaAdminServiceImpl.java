package com.novel.splitter.application.service.chroma;

import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.application.model.dto.ChromaVersionDiagnosticDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

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

    @Value("${chroma.collection:novel-splitter}")
    private String collectionName;

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

        Map<String, Object> body = Map.of(
                "name", collectionName,
                "metadata", Map.of("hnsw:space", "cosine")
        );
        
        try {
            chromaApiClient.post(pathPrefix, body);
            log.info("Created ChromaDB collection: {} with cosine space", collectionName);
        } catch (Exception e) {
            log.error("Failed to create collection: {}", e.getMessage());
            throw new RuntimeException("Failed to recreate collection", e);
        }

        sceneRepository.deleteAll();
        log.info("Cleared local DB scenes via collection rebuild logic");

        return Map.of("message", "Collection rebuilt successfully");
    }

    @Override
    public ChromaVersionDiagnosticDto getVersionDiagnostics(String novel, String version) {
        long dbCount = sceneRepository.countByNovelNameAndVersion(novel, version);
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
                Map<String, Object> where = Map.of("$and", List.of(
                        Map.of("novel", Map.of("$eq", novel)),
                        Map.of("version", Map.of("$eq", version))
                ));

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
