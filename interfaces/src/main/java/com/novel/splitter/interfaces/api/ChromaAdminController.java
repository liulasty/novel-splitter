package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.ChromaVersionDiagnosticDto;
import com.novel.splitter.application.service.chroma.ChromaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

@Tag(name = "Chroma向量数据库管理", description = "提供Chroma数据库的统计、重置、删除以及高级管理功能")
@RestController
@RequestMapping("/api/admin/chroma")
@RequiredArgsConstructor
public class ChromaAdminController {

    private final ChromaAdminService chromaAdminService;

    @Operation(summary = "导出Chroma数据", description = "流式导出Chroma数据库中的向量数据为JSON")
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String novelName,
            @RequestParam(required = false) String version,
            @RequestParam(name = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(name = "chunkOverlap", required = false) Integer chunkOverlap) {
        StreamingResponseBody responseBody = chromaAdminService.exportData(novelName, version, chunkSize, chunkOverlap);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chroma_export.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    @Operation(summary = "获取Chroma统计信息", description = "获取当前Chroma数据库中的向量总数及存储类型")
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return chromaAdminService.getStats();
    }

    @Operation(summary = "重置Chroma数据库", description = "清空Chroma数据库中的所有向量数据")
    @PostMapping("/reset")
    public Map<String, String> reset() {
        return chromaAdminService.reset();
    }

    @Operation(summary = "删除Chroma文档", description = "根据指定的过滤条件删除Chroma数据库中的文档记录")
    @PostMapping("/delete")
    public Map<String, String> delete(@RequestBody Map<String, Object> filter) {
        return chromaAdminService.delete(filter);
    }

    @Operation(summary = "获取Chroma健康状态", description = "获取Chroma服务器的健康检查结果")
    @GetMapping("/healthcheck")
    public Map<String, Object> healthcheck() {
        return chromaAdminService.healthcheck();
    }

    @Operation(summary = "获取Chroma版本", description = "获取当前Chroma服务器的版本号")
    @GetMapping("/version")
    public Map<String, String> version() {
        return chromaAdminService.version();
    }

    @Operation(summary = "获取版本诊断信息", description = "获取数据库与Chroma的同步诊断信息")
    @GetMapping("/diagnostics")
    public ChromaVersionDiagnosticDto getVersionDiagnostics(
            @RequestParam("novel") String novel,
            @RequestParam("version") String version,
            @RequestParam(name = "chunkSize", required = false) Integer chunkSize,
            @RequestParam(name = "chunkOverlap", required = false) Integer chunkOverlap) {
        return chromaAdminService.getVersionDiagnostics(novel, version, chunkSize, chunkOverlap);
    }

    @Operation(summary = "获取Chroma心跳", description = "获取Chroma服务器的心跳时间戳")
    @GetMapping("/heartbeat")
    public Map<String, Object> heartbeat() {
        return chromaAdminService.heartbeat();
    }

    @Operation(summary = "重建集合", description = "删除并重新创建Chroma集合，同时清理本地数据库数据")
    @PostMapping("/collections/rebuild")
    public Map<String, String> rebuildCollection() {
        return chromaAdminService.rebuildCollection();
    }
}

