package com.novel.splitter.repository.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.entity.JpaChapterEntity;
import com.novel.splitter.domain.entity.JpaNovelEntity;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.SceneMetadata;
import com.novel.splitter.repository.api.JpaChapterRepository;
import com.novel.splitter.repository.api.JpaNovelRepository;
import com.novel.splitter.repository.api.JpaSceneRepository;
import com.novel.splitter.repository.api.SceneRepository;
import com.novel.splitter.domain.entity.JpaSceneEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneRepositoryImpl implements SceneRepository {

    private final JpaSceneRepository jpaSceneRepository;
    private final JpaNovelRepository jpaNovelRepository;
    private final JpaChapterRepository jpaChapterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<Long> saveScenes(String novelId, String novelName, String version, List<Scene> scenes) {
        JpaNovelEntity novelEntity = null;
        Map<Integer, JpaChapterEntity> chapterMap = new HashMap<>();

        if (novelId != null && !novelId.isEmpty()) {
            novelEntity = jpaNovelRepository.findById(novelId).orElse(null);
            List<JpaChapterEntity> chapterEntities = jpaChapterRepository.findByNovelIdOrderByIndexNumAsc(novelId);
            if (chapterEntities != null) {
                for (JpaChapterEntity c : chapterEntities) {
                    chapterMap.put(c.getIndexNum(), c);
                }
            }
        }

        final JpaNovelEntity finalNovelEntity = novelEntity;
        
        List<JpaSceneEntity> entities = scenes.stream().map(scene -> {
            JpaSceneEntity entity = new JpaSceneEntity();
            entity.setSceneId(scene.getId());
            entity.setNovelName(novelName);
            entity.setVersion(version);
            entity.setChapterTitle(scene.getChapterTitle());
            entity.setChapterIndex(scene.getChapterIndex());
            entity.setStartParagraphIndex(scene.getStartParagraphIndex());
            entity.setEndParagraphIndex(scene.getEndParagraphIndex());
            entity.setText(scene.getText());
            entity.setWordCount(scene.getWordCount());
            entity.setPrefixContext(scene.getPrefixContext());
            entity.setCanSplit(scene.isCanSplit());
            
            if (finalNovelEntity != null) {
                entity.setNovel(finalNovelEntity);
            }
            JpaChapterEntity chapterEntity = chapterMap.get(scene.getChapterIndex());
            if (chapterEntity != null) {
                entity.setChapter(chapterEntity);
            }

            try {
                if (scene.getMetadata() != null) {
                    scene.getMetadata().setNovel(novelName);
                    scene.getMetadata().setVersion(version);
                    entity.setMetadataJson(objectMapper.writeValueAsString(scene.getMetadata()));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize metadata for scene: {}", scene.getId(), e);
            }
            return entity;
        }).collect(Collectors.toList());

        List<Long> savedIds = new ArrayList<>();
        int batchSize = 500;
        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<JpaSceneEntity> batch = entities.subList(i, end);
            List<JpaSceneEntity> savedBatch = jpaSceneRepository.saveAll(batch);
            savedBatch.forEach(entity -> savedIds.add(entity.getId()));
        }
        return savedIds;
    }

    @Override
    public List<Scene> loadScenes(String novelName, String version) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findByNovelNameAndVersion(novelName, version);
        return entities.stream().map(this::toScene).collect(Collectors.toList());
    }

    @Override
    public List<Scene> findByIds(List<Long> ids) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findByIdIn(ids);
        return entities.stream().map(this::toScene).collect(Collectors.toList());
    }

    @Override
    public List<Scene> findBySceneIds(List<String> sceneIds) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findBySceneIdIn(sceneIds);
        return entities.stream().map(this::toScene).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteVersion(String novelName, String version) {
        jpaSceneRepository.deleteByNovelNameAndVersion(novelName, version);
    }

    @Override
    @Transactional
    public void deleteNovel(String novelName) {
        jpaSceneRepository.deleteByNovelName(novelName);
    }

    @Override
    public List<String> listVersions(String novelName) {
        return jpaSceneRepository.findDistinctVersionsByNovelName(novelName);
    }

    @Override
    public List<Scene> findByNovel(String novelName) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findByNovelName(novelName);
        return entities.stream().map(this::toScene).collect(Collectors.toList());
    }

    private Scene toScene(JpaSceneEntity entity) {
        Scene scene = new Scene();
        scene.setId(entity.getSceneId());
        scene.setChapterTitle(entity.getChapterTitle());
        scene.setChapterIndex(entity.getChapterIndex());
        scene.setStartParagraphIndex(entity.getStartParagraphIndex());
        scene.setEndParagraphIndex(entity.getEndParagraphIndex());
        scene.setText(entity.getText());
        scene.setWordCount(entity.getWordCount());
        scene.setPrefixContext(entity.getPrefixContext());
        scene.setCanSplit(entity.isCanSplit());
        
        if (entity.getMetadataJson() != null) {
            try {
                SceneMetadata metadata = objectMapper.readValue(entity.getMetadataJson(), SceneMetadata.class);
                scene.setMetadata(metadata);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize metadata for scene: {}", entity.getSceneId(), e);
            }
        }
        return scene;
    }
}
