package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.SceneSplitProfileDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.novel.splitter.application.model.dto.VectorPreviewRecordDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "知识库管理", description = "提供知识库中段落实体和版本的查询与删除功能")
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @Operation(summary = "获取轻量级场景分页列表")
    @GetMapping("/scenes/lightweight")
    public Page<VectorPreviewRecordDto> getLightweightScenes(Pageable pageable) {
        return knowledgeBaseService.getLightweightScenes(pageable);
    }

    @Operation(summary = "获取指定小说的所有段落")
    @GetMapping("/{novelName}/scenes")
    public List<SceneDto> getScenes(@PathVariable("novelName") String novelName) {
        return knowledgeBaseService.getScenesByNovel(novelName);
    }

    @Operation(summary = "按 novelId 获取指定小说的所有段落")
    @GetMapping("/id/{novelId}/scenes")
    public List<SceneDto> getScenesByNovelId(@PathVariable("novelId") String novelId) {
        return knowledgeBaseService.getScenesByNovelId(novelId);
    }

    @Operation(summary = "获取指定小说的所有版本列表")
    @GetMapping("/{novelName}/versions")
    public List<String> listVersions(@PathVariable("novelName") String novelName) {
        return knowledgeBaseService.listVersions(novelName);
    }

    @Operation(summary = "获取指定小说（按 novelId）的所有版本列表")
    @GetMapping("/id/{novelId}/versions")
    public List<String> listVersionsByNovelId(@PathVariable("novelId") String novelId) {
        return knowledgeBaseService.listVersionsByNovelId(novelId);
    }

    @Operation(summary = "按 novelId 获取结构化切分数据集（version + chunk 参数）")
    @GetMapping("/id/{novelId}/split-profiles")
    public List<SceneSplitProfileDto> listSplitProfilesByNovelId(@PathVariable("novelId") String novelId) {
        return knowledgeBaseService.listSplitProfilesByNovelId(novelId);
    }

    @Operation(summary = "删除指定小说的特定切分数据集（version + chunk 参数）")
    @DeleteMapping("/{novelName}/versions/{version}")
    public Long deleteVersion(
            @PathVariable("novelName") String novelName,
            @PathVariable("version") String version,
            @RequestParam int chunkSize,
            @RequestParam int chunkOverlap) {
        return knowledgeBaseService.deleteVersion(novelName, version, chunkSize, chunkOverlap);
    }

    @Operation(summary = "按 novelId 删除指定切分数据集")
    @DeleteMapping("/id/{novelId}/versions/{version}")
    public Long deleteSplitProfileByNovelId(
            @PathVariable("novelId") String novelId,
            @PathVariable("version") String version,
            @RequestParam int chunkSize,
            @RequestParam int chunkOverlap) {
        return knowledgeBaseService.deleteSplitProfileByNovelId(novelId, version, chunkSize, chunkOverlap);
    }

    @Operation(summary = "按 novelId 删除整部小说的知识库")
    @DeleteMapping("/id/{novelId}")
    public Long deleteKnowledgeBaseById(@PathVariable("novelId") String novelId) {
        return knowledgeBaseService.deleteKnowledgeBaseById(novelId);
    }

    @Operation(summary = "删除整部小说的知识库")
    @Deprecated
    @DeleteMapping("/{novelName:.+}")
    public Long deleteKnowledgeBase(@PathVariable("novelName") String novelName) {
        return knowledgeBaseService.deleteKnowledgeBase(novelName);
    }
}
