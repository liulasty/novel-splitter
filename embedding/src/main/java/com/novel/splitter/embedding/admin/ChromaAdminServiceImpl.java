package com.novel.splitter.embedding.admin;

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
    private final ChromaProxyPort chromaProxyPort;

    @Override
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            long count = vectorStore.count();
            return ResponseEntity.ok(Map.of(
                    "count", count,
                    "storeType", vectorStore.getClass().getSimpleName()
            ));
        } catch (Exception e) {
            log.error("获取Chroma统计信息失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> reset() {
        try {
            vectorStore.reset();
            return ResponseEntity.ok(Map.of("message", "Database reset successfully"));
        } catch (Exception e) {
            log.error("重置Chroma数据库失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> delete(Map<String, Object> filter) {
        try {
            if (filter == null || filter.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Filter cannot be empty"));
            }
            vectorStore.delete(filter);
            return ResponseEntity.ok(Map.of("message", "Documents deleted successfully"));
        } catch (Exception e) {
            log.error("删除Chroma文档失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> healthcheck() {
        try {
            Map<String, Object> response = chromaProxyPort.getMap("/api/v2/healthcheck");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取Chroma健康状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage(), "status", "down"));
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> version() {
        try {
            String version = chromaProxyPort.getString("/api/v2/version");
            return ResponseEntity.ok(Map.of("version", version != null ? version.replace("\"", "") : "unknown"));
        } catch (Exception e) {
            log.error("获取Chroma版本失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @Override
    public ResponseEntity<Map<String, Object>> heartbeat() {
        try {
            Map<String, Object> response = chromaProxyPort.getMap("/api/v2/heartbeat");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取Chroma心跳失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
