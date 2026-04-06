package com.novel.splitter.application.service.chroma;

import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChromaAdminServiceImpl implements ChromaAdminService {

    private final VectorStore vectorStore;
    private final ChromaApiClient chromaApiClient;

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
