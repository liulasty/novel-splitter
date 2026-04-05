package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.domain.model.Scene;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "知识库管理", description = "提供知识库中段落实体和版本的查询与删除功能")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "获取指定小说的所有段落")
    @GetMapping("/{novelName}/scenes")
    public List<Scene> getScenes(@PathVariable String novelName) {
        return knowledgeBaseService.getScenesByNovel(novelName);
    }

    @Operation(summary = "获取指定小说的所有版本列表")
    @GetMapping("/{novelName}/versions")
    public List<String> listVersions(@PathVariable String novelName) {
        return knowledgeBaseService.listVersions(novelName);
    }

    @Operation(summary = "删除指定小说的特定版本")
    @DeleteMapping("/{novelName}/versions/{version}")
    public void deleteVersion(@PathVariable String novelName, @PathVariable String version) {
        knowledgeBaseService.deleteVersion(novelName, version);
    }

    @Operation(summary = "删除整部小说的知识库")
    @DeleteMapping("/{novelName}")
    public void deleteKnowledgeBase(@PathVariable String novelName) {
        knowledgeBaseService.deleteKnowledgeBase(novelName);
    }
}
