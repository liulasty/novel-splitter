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
            // Strategy: Check 'raw' subdirectory first (Downloader), then root (Uploader)
            java.nio.file.Path rootDir = Paths.get(novelStoragePath);
            java.nio.file.Path rawDir = rootDir.resolve("raw");
            
            // 1. Check in 'raw' subdirectory
            boolean deleted = deleteFileIfExists(rawDir, novelName);
            
            // 2. If not found/deleted in 'raw', check in root directory
            if (!deleted) {
                deleted = deleteFileIfExists(rootDir, novelName);
            }
            
            if (deleted) {
                log.info("Successfully deleted raw file for novel: {}", novelName);
            } else {
                log.warn("Raw file not found for deletion: {}", novelName);
            }
        } catch (Exception e) {
            log.error("Failed to delete raw file for novel: " + novelName, e);
            // Don't throw, partial success is better
        }
    }

    private boolean deleteFileIfExists(java.nio.file.Path dir, String novelName) throws java.io.IOException {
        // Try with .txt extension
        java.nio.file.Path path = dir.resolve(novelName + ".txt");
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("Deleted raw file: {}", path);
            return true;
        }
        
        // Try exact match
        path = dir.resolve(novelName);
        if (Files.exists(path)) {
            Files.delete(path);
            log.info("Deleted raw file: {}", path);
            return true;
        }
        
        return false;
    }

    @Override
    public List<String> listVersions(String novelName) {
        return sceneRepository.listVersions(novelName);
    }
}
