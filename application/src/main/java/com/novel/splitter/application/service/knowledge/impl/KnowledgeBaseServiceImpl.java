package com.novel.splitter.application.service.knowledge.impl;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.domain.task.CleanupTaskMessage;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.application.model.dto.VectorPreviewRecordDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;
    private final CleanupTaskRepository cleanupTaskRepository;
    private final RabbitTemplate rabbitTemplate;
    private final DtoMapper dtoMapper;
    
    @org.springframework.beans.factory.annotation.Value("${splitter.storage.root-path}")
    private String novelStoragePath;

    @Override
    public Page<VectorPreviewRecordDto> getLightweightScenes(Pageable pageable) {
        PagedResult<VectorPreviewRecordDto> result = sceneRepository
                .findLightweightScenes(toPageQuery(pageable))
                .map(scene -> new VectorPreviewRecordDto() {
            @Override
            public Long getId() {
                return scene.getId() != null ? Long.valueOf(scene.getId()) : null;
            }
            @Override
            public Integer getChapterIndex() {
                return scene.getChapterIndex();
            }
            @Override
            public String getType() {
                return scene.getChapterTitle(); // Using chapterTitle as type hack from JpaImpl
            }
            @Override
            public Integer getTokenCount() {
                return scene.getWordCount();
            }
            @Override
            public String getTextContent() {
                return scene.getText();
            }
        });
        return new PageImpl<>(result.getContent(), pageable, result.getTotalElements());
    }

    @Override
    public List<SceneDto> getScenesByNovel(String novelName) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelId(novelId));
    }

    @Override
    public List<SceneDto> getScenesByNovelId(String novelId) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelId(normalizedNovelId));
    }

    @Override
    @Transactional
    public Long deleteVersion(String novelName, String version) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));

        log.info("Logical deleting version: novelId={}/{}", novelId, version);
        sceneRepository.deleteVersionByNovelId(novelId, version);
        
        CleanupTask task = CleanupTask.builder()
                .targetId(novelId)
                .targetType("VERSION")
                .version(version)
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(novelId)
                .targetType("VERSION")
                .version(version)
                .novelId(novelId)
                .novelName(normalizedNovelName)
                .build();
        
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ", savedTask.getId());
        return savedTask.getId();
    }

    @Override
    @Transactional
    public Long deleteKnowledgeBase(String novelName) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        log.info("Logical deleting knowledge base for novelId={} title={}", novelId, normalizedNovelName);
        sceneRepository.deleteNovelById(novelId);
        
        CleanupTask task = CleanupTask.builder()
                .targetId(novelId)
                .targetType("NOVEL")
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(novelId)
                .targetType("NOVEL")
                .novelId(novelId)
                .novelName(normalizedNovelName)
                .build();
        
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ", savedTask.getId());
        return savedTask.getId();
    }

    @Override
    @Transactional
    public Long deleteKnowledgeBaseById(String novelId) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }

        String novelName = novelRepository.findById(normalizedNovelId)
                .map(n -> n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : n.getId())
                .orElse(normalizedNovelId);

        log.info("Logical deleting knowledge base by novelId: {} (name='{}')", normalizedNovelId, novelName);
        sceneRepository.deleteNovelById(normalizedNovelId);

        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelId)
                .targetType("NOVEL_ID")
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(normalizedNovelId)
                .targetType("NOVEL_ID")
                .novelId(normalizedNovelId)
                .novelName(novelName)
                .build();

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ for novelId {}", savedTask.getId(), normalizedNovelId);
        return savedTask.getId();
    }

    @Override
    public List<String> listVersions(String novelName) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        return sceneRepository.listVersionsByNovelId(novelId);
    }

    @Override
    public List<String> listVersionsByNovelId(String novelId) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        return sceneRepository.listVersionsByNovelId(normalizedNovelId);
    }

    @Override
    @Transactional
    public Long deleteVersionByNovelId(String novelId, String version) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }

        String trimmedVersion = version.trim();
        log.info("Logical deleting version by novelId: {}/{}", normalizedNovelId, trimmedVersion);
        sceneRepository.deleteVersionByNovelId(normalizedNovelId, trimmedVersion);

        String novelName = novelRepository.findById(normalizedNovelId)
                .map(n -> n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : n.getId())
                .orElse(normalizedNovelId);

        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelId)
                .targetType("VERSION_BY_NOVEL_ID")
                .version(trimmedVersion)
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(normalizedNovelId)
                .targetType("VERSION_BY_NOVEL_ID")
                .novelId(normalizedNovelId)
                .novelName(novelName)
                .version(trimmedVersion)
                .build();

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ for novelId {} version {}", savedTask.getId(), normalizedNovelId, trimmedVersion);
        return savedTask.getId();
    }

    private String normalizeNovelName(String novelName) {
        if (novelName != null && novelName.toLowerCase().endsWith(".txt")) {
            return novelName.substring(0, novelName.length() - 4);
        }
        return novelName;
    }

    private PageQuery toPageQuery(Pageable pageable) {
        return PageQuery.of(pageable.getPageNumber(), pageable.getPageSize());
    }
}
