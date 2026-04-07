package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import com.novel.splitter.infrastructure.persistence.entity.JpaSceneEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SceneMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaChapterRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaNovelRepository;
import com.novel.splitter.infrastructure.persistence.repository.JpaSceneRepository;
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
public class SceneRepositoryJpaImpl implements SceneRepository {

    private final JpaSceneRepository jpaSceneRepository;
    private final JpaNovelRepository jpaNovelRepository;
    private final JpaChapterRepository jpaChapterRepository;
    private final SceneMapper sceneMapper = SceneMapper.INSTANCE;

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
            if (scene.getMetadata() != null) {
                scene.getMetadata().setNovel(novelName);
                scene.getMetadata().setVersion(version);
            }
            
            JpaSceneEntity entity = sceneMapper.toEntity(scene);
            entity.setNovelName(novelName);
            entity.setVersion(version);
            
            if (finalNovelEntity != null) {
                entity.setNovel(finalNovelEntity);
            }
            JpaChapterEntity chapterEntity = chapterMap.get(scene.getChapterIndex());
            if (chapterEntity != null) {
                entity.setChapter(chapterEntity);
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
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Scene> findByIds(List<Long> ids) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findByIdIn(ids);
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Scene> findBySceneIds(List<String> sceneIds) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findBySceneIdIn(sceneIds);
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
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
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
    }
}
