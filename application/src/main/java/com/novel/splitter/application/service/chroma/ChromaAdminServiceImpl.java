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
        try {
            long count = vectorStore.count();
            return Map.of(
                    "count", count,
                    "storeType", vectorStore.getClass().getSimpleName()
            );
        } catch (Exception e) {
            log.error("获取Chroma统计信息失败", e);
            throw new RuntimeException("获取Chroma统计信息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> reset() {
        try {
            vectorStore.reset();
            return Map.of("message", "Database reset successfully");
        } catch (Exception e) {
            log.error("重置Chroma数据库失败", e);
            throw new RuntimeException("重置Chroma数据库失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> delete(Map<String, Object> filter) {
        try {
            if (filter == null || filter.isEmpty()) {
                throw new IllegalArgumentException("Filter cannot be empty");
            }
            vectorStore.delete(filter);
            return Map.of("message", "Documents deleted successfully");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除Chroma文档失败", e);
            throw new RuntimeException("删除Chroma文档失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> healthcheck() {
        try {
            return chromaApiClient.getMap("/api/v2/healthcheck");
        } catch (Exception e) {
            log.error("获取Chroma健康状态失败", e);
            throw new RuntimeException("获取Chroma健康状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, String> version() {
        try {
            String version = chromaApiClient.getString("/api/v2/version");
            return Map.of("version", version != null ? version.replace("\"", "") : "unknown");
        } catch (Exception e) {
            log.error("获取Chroma版本失败", e);
            throw new RuntimeException("获取Chroma版本失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> heartbeat() {
        try {
            return chromaApiClient.getMap("/api/v2/heartbeat");
        } catch (Exception e) {
            log.error("获取Chroma心跳失败", e);
            throw new RuntimeException("获取Chroma心跳失败: " + e.getMessage(), e);
        }
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
