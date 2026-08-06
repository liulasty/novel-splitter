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
                // 简单实现：直接拉取列表，后续可改为在 Repository 中流式查询以优化
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
        try {
            String result = chromaApiClient.getString("/api/v2/healthcheck");
            return Map.of("status", "ok", "result", result != null ? result : "ok");
        } catch (Exception e) {
            log.error("健康检查失败", e);
            return Map.of("status", "error", "error", e.getMessage());
        }
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
            log.info("已删除 ChromaDB 集合：{}", collectionName);
        } catch (Exception e) {
            log.warn("删除集合失败（可能不存在）：{}", e.getMessage());
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
            log.info("已创建 ChromaDB 集合：{}，hnsw:space={}", collectionName, space);
        } catch (Exception e) {
            log.error("创建集合失败：{}", e.getMessage());
            throw new RuntimeException("Failed to recreate collection", e);
        }

        sceneRepository.deleteAll();
        log.info("集合重建逻辑已清空本地 DB 场景");

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
            // 查找集合 id（代理返回原始 JSON 字符串，需解析为对象）
            Object collectionsObj = parseJsonResponse(proxyGet("/api/v2/tenants/default_tenant/databases/default_database/collections"));
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

                // 获取数量
                Map<String, Object> countBody = Map.of(
                        "where", where,
                        "include", List.of()
                );
                Object countRes = parseJsonResponse(proxyPost("/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId + "/get", countBody));
                if (countRes instanceof Map<?, ?> map && map.get("ids") instanceof List<?> ids) {
                    chromaCount = ids.size();
                }

                // 获取 1 条记录的元数据
                Map<String, Object> metaBody = Map.of(
                        "where", where,
                        "limit", 1,
                        "include", List.of("metadatas")
                );
                Object metaRes = parseJsonResponse(proxyPost("/api/v2/tenants/default_tenant/databases/default_database/collections/" + collectionId + "/get", metaBody));
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
            log.error("获取 novel={} version={} 的 chroma 诊断信息失败", novel, version, e);
            // 可选：可置 chromaCount = -1 以标识出错
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
        return parseJsonResponse(extractBody(chromaApiClient.get(path)));
    }

    @Override
    public Object proxyPost(String path, Object body) {
        return parseJsonResponse(extractBody(chromaApiClient.post(path, body)));
    }

    @Override
    public Object proxyPut(String path, Object body) {
        return parseJsonResponse(extractBody(chromaApiClient.put(path, body)));
    }

    @Override
    public Object proxyPatch(String path, Object body) {
        return parseJsonResponse(extractBody(chromaApiClient.patch(path, body)));
    }

    @Override
    public Object proxyDelete(String path) {
        return parseJsonResponse(extractBody(chromaApiClient.delete(path)));
    }

    /** 将 JSON 字符串形式的代理响应解析为合适的 Java 对象（Map/List 等） */
    private Object parseJsonResponse(Object proxyResult) {
        if (proxyResult instanceof String json) {
            try {
                return objectMapper.readValue(json, Object.class);
            } catch (Exception e) {
                log.warn("将代理响应解析为 JSON 失败，返回原始字符串", e);
                return proxyResult;
            }
        }
        return proxyResult;
    }

    private Object extractBody(ResponseEntity<?> responseEntity) {
        if (responseEntity.getStatusCode().isError()) {
            String errorBody = responseEntity.getBody() instanceof String s ? s : "Unknown error";
            return Map.of(
                    "error", "Proxy request failed",
                    "status", responseEntity.getStatusCode().value(),
                    "details", errorBody
            );
        }
        return responseEntity.getBody();
    }
}
