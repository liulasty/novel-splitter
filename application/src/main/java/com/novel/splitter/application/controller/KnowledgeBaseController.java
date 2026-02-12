package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.domain.model.Scene;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理控制器
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 获取指定小说的所有 Scene
     */
    @GetMapping("/{novelName}/scenes")
    public ResponseEntity<List<Scene>> getScenes(@PathVariable("novelName") String novelName) {
        return ResponseEntity.ok(knowledgeBaseService.getScenesByNovel(normalizeNovelName(novelName)));
    }

    @GetMapping("/{novelName}/versions")
    public ResponseEntity<List<String>> listVersions(@PathVariable("novelName") String novelName) {
        return ResponseEntity.ok(knowledgeBaseService.listVersions(normalizeNovelName(novelName)));
    }

    private String normalizeNovelName(String novelName) {
        if (novelName != null && novelName.endsWith(".txt")) {
            return novelName.substring(0, novelName.length() - 4);
        }
        return novelName;
    }

    /**
     * 根据 ID 获取 Scene
     */
    @GetMapping("/scenes/{id}")
    public ResponseEntity<Scene> getScene(@PathVariable("id") String id) {
        try {
            return ResponseEntity.ok(knowledgeBaseService.getSceneById(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 更新 Scene
     */
    @PutMapping("/scenes")
    public ResponseEntity<Void> updateScene(@RequestBody Scene scene) {
        try {
            knowledgeBaseService.updateScene(scene);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            log.error("Update failed", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 删除 Scene
     */
    @DeleteMapping("/scenes/{id}")
    public ResponseEntity<Void> deleteScene(@PathVariable("id") String id) {
        knowledgeBaseService.deleteScene(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除指定版本
     */
    @DeleteMapping("/{novelName}/versions/{version}")
    public ResponseEntity<Void> deleteVersion(@PathVariable("novelName") String novelName, 
                                              @PathVariable("version") String version) {
        knowledgeBaseService.deleteVersion(normalizeNovelName(novelName), version);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除整个知识库（包括所有版本和源文件）
     */
    @DeleteMapping("/{novelName}")
    public ResponseEntity<Void> deleteKnowledgeBase(@PathVariable("novelName") String novelName) {
        knowledgeBaseService.deleteKnowledgeBase(normalizeNovelName(novelName));
        return ResponseEntity.ok().build();
    }
}
