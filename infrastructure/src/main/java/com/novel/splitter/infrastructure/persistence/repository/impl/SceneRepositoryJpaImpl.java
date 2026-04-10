package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private final SceneMapper sceneMapper;
    @PersistenceContext
    private EntityManager entityManager;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:500}")
    private int jdbcBatchSize;

    @Override
    public List<Long> saveScenes(String novelId, String version, List<Scene> scenes) {
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
                scene.getMetadata().setVersion(version);
            }
            
            JpaSceneEntity entity = sceneMapper.toEntity(scene);
            entity.setVersion(version);
            // Backward-compat: if DB still has NOT NULL novel_name, write stable novelId here.
            entity.setLegacyNovelName(novelId);
            
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
        int batchSize = Math.max(1, jdbcBatchSize);
        for (int i = 0; i < entities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, entities.size());
            List<JpaSceneEntity> batch = entities.subList(i, end);
            List<JpaSceneEntity> savedBatch = jpaSceneRepository.saveAll(new ArrayList<>(batch));
            jpaSceneRepository.flush();
            entityManager.clear();
            savedBatch.forEach(entity -> savedIds.add(entity.getId()));
        }
        return savedIds;
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
    public void deleteNovelById(String novelId) {
        jpaSceneRepository.deleteByNovelId(novelId);
    }

    @Override
    @Transactional
    public void deleteVersionByNovelId(String novelId, String version) {
        jpaSceneRepository.deleteByNovelIdAndVersion(novelId, version);
    }

    @Override
    public List<String> listVersionsByNovelId(String novelId) {
        return jpaSceneRepository.findDistinctVersionsByNovelId(novelId);
    }

    @Override
    public List<Scene> findAllByNovelId(String novelId) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findByNovelId(novelId);
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<Scene> findByNovelIdAndVersion(String novelId, String version) {
        List<JpaSceneEntity> entities = jpaSceneRepository.findByNovelIdAndVersion(novelId, version);
        return entities.stream().map(sceneMapper::toDomain).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteAll() {
        jpaSceneRepository.deleteAll();
    }

    @Override
    public long countByNovelIdAndVersion(String novelId, String version) {
        return jpaSceneRepository.countByNovelIdAndVersion(novelId, version);
    }

    @Override
    public PagedResult<Scene> findLightweightScenes(PageQuery pageQuery) {
        // Here we map the DTO returned by JpaSceneRepository to Domain Scene
        // We'll construct dummy Scene objects to satisfy Domain contract or return partial models
        Page<Scene> page = jpaSceneRepository.findLightweightScenes(toPageable(pageQuery))
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
        return toPagedResult(page);
    }

    @Override
    public PagedResult<Scene> findByNovelId(String novelId, PageQuery pageQuery) {
        Page<Scene> page = jpaSceneRepository.findByNovelId(novelId, toPageable(pageQuery)).map(sceneMapper::toDomain);
        return toPagedResult(page);
    }

    @Override
    public PagedResult<Scene> findByNovelIdAndVersion(String novelId, String version, PageQuery pageQuery) {
        Page<Scene> page = jpaSceneRepository.findByNovelIdAndVersion(novelId, version, toPageable(pageQuery)).map(sceneMapper::toDomain);
        return toPagedResult(page);
    }

    @Override
    public PagedResult<Scene> findByNovelIdAndChapterId(String novelId, Long chapterId, PageQuery pageQuery) {
        Page<Scene> page = jpaSceneRepository.findByNovelIdAndChapterId(novelId, chapterId, toPageable(pageQuery))
                .map(sceneMapper::toDomain);
        return toPagedResult(page);
    }

    @Override
    public List<Object[]> countScenesByNovelAndVersion() {
        return jpaSceneRepository.countScenesByNovelAndVersion();
    }

    private Pageable toPageable(PageQuery pageQuery) {
        int page = Math.max(0, pageQuery.getPage());
        int size = Math.max(1, pageQuery.getSize());
        return PageRequest.of(page, size);
    }

    private PagedResult<Scene> toPagedResult(Page<Scene> page) {
        return PagedResult.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
