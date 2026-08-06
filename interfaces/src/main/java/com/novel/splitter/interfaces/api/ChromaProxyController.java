package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.chroma.ChromaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Chroma代理", description = "Chroma OpenAPI 代理（内部调试/管理用）")
@RestController
@RequestMapping("/api/admin/chroma")
@RequiredArgsConstructor
public class ChromaProxyController {

    private final ChromaAdminService chromaAdminService;

    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    @Operation(summary = "获取所有集合", description = "获取默认租户和数据库下的所有Chroma集合")
    @GetMapping("/collections")
    public Object getCollections() {
        return chromaAdminService.proxyGet("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections");
    }

    // --- 系统端点 ---

    @Operation(summary = "预检检查", description = "获取Chroma的预检检查状态")
    @GetMapping("/pre-flight-checks")
    public Object preFlightChecks() {
        return chromaAdminService.proxyGet("/api/v2/pre-flight-checks");
    }

    @Operation(summary = "认证身份", description = "获取当前认证的身份信息")
    @GetMapping("/auth/identity")
    public Object authIdentity() {
        return chromaAdminService.proxyGet("/api/v2/auth/identity");
    }

    // --- 租户端点 ---

    @Operation(summary = "获取租户列表", description = "获取所有租户信息")
    @GetMapping("/tenants")
    public Object getTenants() {
        return chromaAdminService.proxyGet("/api/v2/tenants");
    }

    @Operation(summary = "创建租户", description = "创建新的租户")
    @PostMapping("/tenants")
    public Object createTenant(@RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants", body);
    }

    @Operation(summary = "更新租户", description = "更新指定租户")
    @PatchMapping("/tenants/{name}")
    public Object updateTenant(@PathVariable("name") String name, @RequestBody Object body) {
        return chromaAdminService.proxyPatch("/api/v2/tenants/" + name, body);
    }

    // --- 数据库端点 ---

    @Operation(summary = "获取数据库列表", description = "获取指定租户下的所有数据库")
    @GetMapping("/tenants/{t}/databases")
    public Object getDatabases(@PathVariable("t") String t) {
        return chromaAdminService.proxyGet("/api/v2/tenants/" + t + "/databases");
    }

    @Operation(summary = "创建数据库", description = "在指定租户下创建新的数据库")
    @PostMapping("/tenants/{t}/databases")
    public Object createDatabase(@PathVariable("t") String t, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + t + "/databases", body);
    }

    @Operation(summary = "获取数据库详情", description = "获取指定租户下的数据库详情")
    @GetMapping("/tenants/{t}/databases/{d}")
    public Object getDatabase(@PathVariable("t") String t, @PathVariable("d") String d) {
        return chromaAdminService.proxyGet("/api/v2/tenants/" + t + "/databases/" + d);
    }

    @Operation(summary = "删除数据库", description = "删除指定租户下的数据库")
    @DeleteMapping("/tenants/{t}/databases/{d}")
    public Object deleteDatabase(@PathVariable("t") String t, @PathVariable("d") String d) {
        return chromaAdminService.proxyDelete("/api/v2/tenants/" + t + "/databases/" + d);
    }

    // --- 集合端点 ---

    @Operation(summary = "创建集合", description = "在默认租户和数据库下创建新集合")
    @PostMapping("/collections")
    public Object createCollection(@RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections", body);
    }

    @Operation(summary = "获取集合详情", description = "获取指定集合的详细信息")
    @GetMapping("/collections/{id}")
    public Object getCollection(@PathVariable("id") String id) {
        return chromaAdminService.proxyGet("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id);
    }

    @Operation(summary = "更新集合", description = "更新指定集合的信息")
    @PutMapping("/collections/{id}")
    public Object updateCollection(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPut("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id, body);
    }

    @Operation(summary = "删除集合", description = "删除指定集合")
    @DeleteMapping("/collections/{id}")
    public Object deleteCollection(@PathVariable("id") String id) {
        return chromaAdminService.proxyDelete("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id);
    }

    // --- 集合操作端点 ---

    @Operation(summary = "添加文档", description = "向指定集合添加文档")
    @PostMapping("/collections/{id}/add")
    public Object addDocuments(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/add", body);
    }

    @Operation(summary = "更新或插入文档", description = "向指定集合更新或插入文档")
    @PostMapping("/collections/{id}/upsert")
    public Object upsertDocuments(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/upsert", body);
    }

    @Operation(summary = "更新文档", description = "更新指定集合中的文档")
    @PostMapping("/collections/{id}/update")
    public Object updateDocuments(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/update", body);
    }

    @Operation(summary = "删除文档", description = "从指定集合中删除文档")
    @PostMapping("/collections/{id}/delete")
    public Object deleteDocuments(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/delete", body);
    }

    @Operation(summary = "获取文档", description = "从指定集合中获取文档")
    @PostMapping("/collections/{id}/get")
    public Object getDocuments(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/get", body);
    }

    @Operation(summary = "查询文档", description = "在指定集合中查询文档")
    @PostMapping("/collections/{id}/query")
    public Object queryDocuments(@PathVariable("id") String id, @RequestBody Object body) {
        return chromaAdminService.proxyPost("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/query", body);
    }

    @Operation(summary = "统计文档数", description = "获取指定集合的文档总数")
    @GetMapping("/collections/{id}/count")
    public Object countDocuments(@PathVariable("id") String id) {
        return chromaAdminService.proxyGet("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + id + "/count");
    }
}

