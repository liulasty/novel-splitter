package com.novel.splitter.application.controller;

import com.novel.splitter.embedding.api.VectorStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Chroma向量数据库管理控制器
 * 提供Chroma数据库的统计、重置、删除以及高级管理功能（健康检查、集合管理等）
 */
@Tag(name = "Chroma向量数据库管理", description = "提供Chroma数据库的统计、重置、删除以及高级管理功能")
@RestController
@RequestMapping("/api/admin/chroma")
@RequiredArgsConstructor
@Slf4j
public class ChromaManagementController {

    private final VectorStore vectorStore;
    private final RestClient restClient = RestClient.builder().build();

    @Value("${chroma.url:http://localhost:8081}")
    private String chromaUrl;

    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    /**
     * 获取Chroma统计信息
     *
     * @return 包含向量数量和存储类型的统计信息
     */
    @Operation(summary = "获取Chroma统计信息", description = "获取当前Chroma数据库中的向量总数及存储类型")
    @GetMapping("/stats")
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

    /**
     * 重置Chroma数据库
     *
     * @return 重置操作结果消息
     */
    @Operation(summary = "重置Chroma数据库", description = "清空Chroma数据库中的所有向量数据")
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        try {
            vectorStore.reset();
            return ResponseEntity.ok(Map.of("message", "Database reset successfully"));
        } catch (Exception e) {
            log.error("重置Chroma数据库失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 根据条件删除Chroma文档
     *
     * @param filter 删除过滤条件
     * @return 删除操作结果消息
     */
    @Operation(summary = "删除Chroma文档", description = "根据指定的过滤条件删除Chroma数据库中的文档记录")
    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> delete(@RequestBody Map<String, Object> filter) {
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
    
    /**
     * 获取Chroma健康状态
     */
    @Operation(summary = "获取Chroma健康状态", description = "获取Chroma服务器的健康检查结果")
    @GetMapping("/healthcheck")
    public ResponseEntity<Map<String, Object>> healthcheck() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(chromaUrl + "/api/v2/healthcheck")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取Chroma健康状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage(), "status", "down"));
        }
    }

    /**
     * 获取Chroma版本
     */
    @Operation(summary = "获取Chroma版本", description = "获取当前Chroma服务器的版本号")
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        try {
            String version = restClient.get()
                    .uri(chromaUrl + "/api/v2/version")
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok(Map.of("version", version != null ? version.replace("\"", "") : "unknown"));
        } catch (Exception e) {
            log.error("获取Chroma版本失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取Chroma心跳
     */
    @Operation(summary = "获取Chroma心跳", description = "获取Chroma服务器的心跳时间戳")
    @GetMapping("/heartbeat")
    public ResponseEntity<Map<String, Object>> heartbeat() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri(chromaUrl + "/api/v2/heartbeat")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取Chroma心跳失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取所有集合
     */
    @Operation(summary = "获取所有集合", description = "获取默认租户和数据库下的所有Chroma集合")
    @GetMapping("/collections")
    public ResponseEntity<?> getCollections() {
        return proxyGet("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections");
    }

    // --- System Endpoints ---

    @Operation(summary = "预检检查", description = "获取Chroma的预检检查状态")
    @GetMapping("/pre-flight-checks")
    public ResponseEntity<?> preFlightChecks() {
        return proxyGet("/api/v2/pre-flight-checks");
    }

    @Operation(summary = "认证身份", description = "获取当前认证的身份信息")
    @GetMapping("/auth/identity")
    public ResponseEntity<?> authIdentity() {
        return proxyGet("/api/v2/auth/identity");
    }

    // --- Tenant Endpoints ---

    @Operation(summary = "获取租户列表", description = "获取所有租户信息")
    @GetMapping("/tenants")
    public ResponseEntity<?> getTenants() {
        return proxyGet("/api/v2/tenants");
    }

    @Operation(summary = "创建租户", description = "创建新的租户")
    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody Object body) {
        return proxyPost("/api/v2/tenants", body);
    }

    @Operation(summary = "更新租户", description = "更新指定租户")
    @PatchMapping("/tenants/{name}")
    public ResponseEntity<?> updateTenant(@PathVariable String name, @RequestBody Object body) {
        return proxyPatch("/api/v2/tenants/" + name, body);
    }

    // --- Database Endpoints ---

    @Operation(summary = "获取数据库列表", description = "获取指定租户下的所有数据库")
    @GetMapping("/tenants/{t}/databases")
    public ResponseEntity<?> getDatabases(@PathVariable String t) {
        return proxyGet("/api/v2/tenants/" + t + "/databases");
    }

    @Operation(summary = "创建数据库", description = "在指定租户下创建新的数据库")
    @PostMapping("/tenants/{t}/databases")
    public ResponseEntity<?> createDatabase(@PathVariable String t, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + t + "/databases", body);
    }

    @Operation(summary = "获取数据库详情", description = "获取指定租户下的数据库详情")
    @GetMapping("/tenants/{t}/databases/{d}")
    public ResponseEntity<?> getDatabase(@PathVariable String t, @PathVariable String d) {
        return proxyGet("/api/v2/tenants/" + t + "/databases/" + d);
    }

    @Operation(summary = "删除数据库", description = "删除指定租户下的数据库")
    @DeleteMapping("/tenants/{t}/databases/{d}")
    public ResponseEntity<?> deleteDatabase(@PathVariable String t, @PathVariable String d) {
        return proxyDelete("/api/v2/tenants/" + t + "/databases/" + d);
    }

    // --- Collection Endpoints ---

    @Operation(summary = "创建集合", description = "在默认租户和数据库下创建新集合")
    @PostMapping("/collections")
    public ResponseEntity<?> createCollection(@RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections", body);
    }

    @Operation(summary = "获取集合详情", description = "获取指定集合的详细信息")
    @GetMapping("/collections/{id}")
    public ResponseEntity<?> getCollection(@PathVariable String id) {
        return proxyGet("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id);
    }

    @Operation(summary = "更新集合", description = "更新指定集合的信息")
    @PutMapping("/collections/{id}")
    public ResponseEntity<?> updateCollection(@PathVariable String id, @RequestBody Object body) {
        return proxyPut("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id, body);
    }

    @Operation(summary = "删除集合", description = "删除指定集合")
    @DeleteMapping("/collections/{id}")
    public ResponseEntity<?> deleteCollection(@PathVariable String id) {
        return proxyDelete("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id);
    }

    // --- Collection Action Endpoints ---

    @Operation(summary = "添加文档", description = "向指定集合添加文档")
    @PostMapping("/collections/{id}/add")
    public ResponseEntity<?> addDocuments(@PathVariable String id, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/add", body);
    }

    @Operation(summary = "更新或插入文档", description = "向指定集合更新或插入文档")
    @PostMapping("/collections/{id}/upsert")
    public ResponseEntity<?> upsertDocuments(@PathVariable String id, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/upsert", body);
    }

    @Operation(summary = "更新文档", description = "更新指定集合中的文档")
    @PostMapping("/collections/{id}/update")
    public ResponseEntity<?> updateDocuments(@PathVariable String id, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/update", body);
    }

    @Operation(summary = "删除文档", description = "从指定集合中删除文档")
    @PostMapping("/collections/{id}/delete")
    public ResponseEntity<?> deleteDocuments(@PathVariable String id, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/delete", body);
    }

    @Operation(summary = "获取文档", description = "从指定集合中获取文档")
    @PostMapping("/collections/{id}/get")
    public ResponseEntity<?> getDocuments(@PathVariable String id, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/get", body);
    }

    @Operation(summary = "查询文档", description = "在指定集合中查询文档")
    @PostMapping("/collections/{id}/query")
    public ResponseEntity<?> queryDocuments(@PathVariable String id, @RequestBody Object body) {
        return proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/query", body);
    }

    @Operation(summary = "统计文档数", description = "获取指定集合的文档总数")
    @GetMapping("/collections/{id}/count")
    public ResponseEntity<?> countDocuments(@PathVariable String id) {
        return proxyGet("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/count");
    }

    // --- Helper Methods ---

    private ResponseEntity<?> proxyGet(String path) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri(chromaUrl + path)
                    .retrieve()
                    .toEntity(String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("GET " + path + " failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> proxyPost(String path, Object body) {
        try {
            ResponseEntity<String> response = restClient.post()
                    .uri(chromaUrl + path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("POST " + path + " failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> proxyPut(String path, Object body) {
        try {
            ResponseEntity<String> response = restClient.put()
                    .uri(chromaUrl + path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("PUT " + path + " failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> proxyPatch(String path, Object body) {
        try {
            ResponseEntity<String> response = restClient.patch()
                    .uri(chromaUrl + path)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("PATCH " + path + " failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> proxyDelete(String path) {
        try {
            ResponseEntity<String> response = restClient.delete()
                    .uri(chromaUrl + path)
                    .retrieve()
                    .toEntity(String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException | org.springframework.web.client.HttpServerErrorException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("DELETE " + path + " failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
