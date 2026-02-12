package com.novel.splitter.application.service.knowledge.impl;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final SceneRepository sceneRepository;
    private final VectorStore vectorStore;
    
    @org.springframework.beans.factory.annotation.Value("${splitter.storage.root-path}")
    private String novelStoragePath;

    @Override
    public List<Scene> getScenesByNovel(String novelName) {
        return sceneRepository.findByNovel(novelName);
    }

    @Override
    public Scene getSceneById(String id) {
        return sceneRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Scene not found: " + id));
    }

    @Override
    public void updateScene(Scene scene) {
        log.info("Updating scene: {}", scene.getId());
        // 简单校验
        sceneRepository.findById(scene.getId())
                .orElseThrow(() -> new RuntimeException("Scene not found: " + scene.getId()));
        
        sceneRepository.update(scene);
    }

    @Override
    public void deleteScene(String id) {
        log.info("Deleting scene: {}", id);
        sceneRepository.delete(id);
        // Also delete from vector store? 
        // Current VectorStore delete takes a filter. 
        // We can't easily delete a single vector by ID unless we expose deleteById or filter by ID.
        // VectorStore.delete(Map<String, Object> filter)
        // If we want to delete by ID, we'd need to extend VectorStore or rely on metadata having ID (unlikely).
        // For now, assume sceneRepository handles the main deletion, and VectorStore might be out of sync if we delete single scene.
        // Ideally VectorStore should support deleteById.
    }

    @Override
    public void deleteVersion(String novelName, String version) {
        log.info("Deleting version: {}/{}", novelName, version);
        sceneRepository.deleteVersion(novelName, version);
        vectorStore.delete(Map.of("novel", novelName, "version", version));
    }

    @Override
    public void deleteKnowledgeBase(String novelName) {
        log.info("Deleting knowledge base for: {}", novelName);
        sceneRepository.deleteNovel(novelName);
        vectorStore.delete(Map.of("novel", novelName));
        
        try {
            // Delete raw file
            // Note: Raw files are stored in {root}/raw/{novelName}.txt
            java.nio.file.Path rawDir = Paths.get(novelStoragePath, "raw");
            
            // Try with .txt extension
            java.nio.file.Path rawPath = rawDir.resolve(novelName + ".txt");
            if (Files.exists(rawPath)) {
                Files.delete(rawPath);
                log.info("Deleted raw file: {}", rawPath);
            } else {
                 // Try exact match if novelName already has extension or without extension
                 rawPath = rawDir.resolve(novelName);
                 if (Files.exists(rawPath)) {
                     Files.delete(rawPath);
                     log.info("Deleted raw file: {}", rawPath);
                 }
            }
        } catch (Exception e) {
            log.error("Failed to delete raw file for novel: " + novelName, e);
            // Don't throw, partial success is better
        }
    }

    @Override
    public List<String> listVersions(String novelName) {
        return sceneRepository.listVersions(novelName);
    }
}
