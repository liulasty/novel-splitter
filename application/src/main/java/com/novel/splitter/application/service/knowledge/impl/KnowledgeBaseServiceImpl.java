package com.novel.splitter.application.service.knowledge.impl;

import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.repository.api.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final SceneRepository sceneRepository;

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
    }

    @Override
    public List<String> listVersions(String novelName) {
        return sceneRepository.listVersions(novelName);
    }
}
