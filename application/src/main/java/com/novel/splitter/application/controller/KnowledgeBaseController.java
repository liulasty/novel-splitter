package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.domain.model.Scene;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器
 * 提供知识库的查询、版本管理和删除等功能
 */
@Tag(name = "知识库管理", description = "提供知识库的查询、版本管理和删除等功能")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取指定小说的所有 Scene（场景段落）
     *
     * @param novelName 小说名称标识
     * @return 小说对应的所有场景段落列表
     */
    @Operation(summary = "获取小说场景", description = "获取指定小说的所有已处理的 Scene（场景段落）")
    @GetMapping("/{novelName}/scenes")
    public ResponseEntity<List<Scene>> getScenes(
            @Parameter(description = "小说名称", required = true) @PathVariable("novelName") String novelName) {
        return ResponseEntity.ok(knowledgeBaseService.getScenesByNovel(normalizeNovelName(novelName)));
    }

    /**
     * 列出指定小说的所有处理版本
     *
     * @param novelName 小说名称标识
     * @return 该小说的版本列表
     */
    @Operation(summary = "列出小说版本", description = "获取指定小说的所有已处理或分块的版本列表")
    @GetMapping("/{novelName}/versions")
    public ResponseEntity<List<String>> listVersions(
            @Parameter(description = "小说名称", required = true) @PathVariable("novelName") String novelName) {
        return ResponseEntity.ok(knowledgeBaseService.listVersions(normalizeNovelName(novelName)));
    }

    /**
     * 规范化小说名称（去除 .txt 后缀）
     *
     * @param novelName 原始小说名称
     * @return 规范化后的小说名称
     */
    private String normalizeNovelName(String novelName) {
        if (novelName != null && novelName.endsWith(".txt")) {
            return novelName.substring(0, novelName.length() - 4);
        }
        return novelName;
    }

    /**
     * 删除指定小说的特定版本数据
     *
     * @param novelName 小说名称标识
     * @param version   要删除的版本号
     * @return 响应实体
     */
    @Operation(summary = "删除小说版本", description = "删除指定小说的特定处理版本数据")
    @DeleteMapping("/{novelName}/versions/{version}")
    public ResponseEntity<Void> deleteVersion(
            @Parameter(description = "小说名称", required = true) @PathVariable("novelName") String novelName, 
            @Parameter(description = "版本号", required = true) @PathVariable("version") String version) {
        knowledgeBaseService.deleteVersion(normalizeNovelName(novelName), version);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除整个知识库（包括所有版本和源文件）
     *
     * @param novelName 小说名称标识
     * @return 响应实体
     */
    @Operation(summary = "删除知识库", description = "删除指定小说的整个知识库（包括所有版本数据和源文件）")
    @DeleteMapping("/{novelName}")
    public ResponseEntity<Void> deleteKnowledgeBase(
            @Parameter(description = "小说名称", required = true) @PathVariable("novelName") String novelName) {
        knowledgeBaseService.deleteKnowledgeBase(normalizeNovelName(novelName));
        return ResponseEntity.ok().build();
    }
}
