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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SceneRepositoryJpaImpl implements SceneRepository {

    private final JpaSceneRepository jpaSceneRepository;
    private final JpaNovelRepository jpaNovelRepository;
    private final JpaChapterRepository jpaChapterRepository;
    private final SceneMapper sceneMapper;

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
            List<JpaSceneEntity> savedBatch = jpaSceneRepository.saveAll(Objects.requireNonNull(batch, "batch must not be null"));
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

    @Override
    @Transactional
    public void deleteAll() {
        jpaSceneRepository.deleteAll();
    }

    @Override
    public long countByNovelNameAndVersion(String novelName, String version) {
        return jpaSceneRepository.countByNovelNameAndVersion(novelName, version);
    }

    @Override
    public Page<Scene> findLightweightScenes(Pageable pageable) {
        // Here we map the DTO returned by JpaSceneRepository to Domain Scene
        // We'll construct dummy Scene objects to satisfy Domain contract or return partial models
        return jpaSceneRepository.findLightweightScenes(pageable)
                .map(dto -> {
                    Scene scene = new Scene();
                    scene.setId(dto.getId() != null ? String.valueOf(dto.getId()) : null);
                    scene.setChapterIndex(dto.getChapterIndex() != null ? dto.getChapterIndex() : 0);
                    // store type in chapterTitle as a hack for now, or just set it if domain adds type
                    scene.setChapterTitle(dto.getType());
                    scene.setWordCount(dto.getTokenCount() != null ? dto.getTokenCount() : 0);
                    scene.setText(dto.getTextContent());
                    return scene;
                });
    }

    @Override
    public Page<Scene> findByNovelId(String novelId, Pageable pageable) {
        return jpaSceneRepository.findByNovelId(novelId, pageable).map(sceneMapper::toDomain);
    }

    @Override
    public List<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId) {
        return jpaSceneRepository.findByNovelIdAndChapterId(novelId, chapterId)
                .stream()
                .map(sceneMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Object[]> countScenesByNovelAndVersion() {
        return jpaSceneRepository.countScenesByNovelAndVersion();
    }
}
