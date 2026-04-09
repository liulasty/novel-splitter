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
        return dtoMapper.toSceneDtos(sceneRepository.findByNovel(normalizeNovelName(novelName)));
    }

    @Override
    @Transactional
    public Long deleteVersion(String novelName, String version) {
        String normalizedNovelName = normalizeNovelName(novelName);
        log.info("Logical deleting version: {}/{}", normalizedNovelName, version);
        sceneRepository.deleteVersion(normalizedNovelName, version);
        
        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelName)
                .targetType("VERSION")
                .version(version)
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(normalizedNovelName)
                .targetType("VERSION")
                .version(version)
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
        log.info("Logical deleting knowledge base for: {}", normalizedNovelName);
        sceneRepository.deleteNovel(normalizedNovelName);
        
        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelName)
                .targetType("NOVEL")
                .status("PENDING")
                .build();
        CleanupTask savedTask = cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(savedTask.getId())
                .targetId(normalizedNovelName)
                .targetType("NOVEL")
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
        return sceneRepository.listVersions(normalizeNovelName(novelName));
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
