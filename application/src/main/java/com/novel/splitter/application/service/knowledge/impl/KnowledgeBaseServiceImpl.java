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
    private final CleanupTaskRepository cleanupTaskRepository;
    private final RabbitTemplate rabbitTemplate;
    
    @org.springframework.beans.factory.annotation.Value("${splitter.storage.root-path}")
    private String novelStoragePath;

    @Override
    public Page<VectorPreviewRecordDto> getLightweightScenes(Pageable pageable) {
        PagedResult<VectorPreviewRecordDto> result = sceneRepository
                .findLightweightScenes(PageQuery.of(pageable.getPageNumber(), pageable.getPageSize()))
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
        return DtoMapper.INSTANCE.toSceneDtos(sceneRepository.findByNovel(normalizeNovelName(novelName)));
    }

    @Override
    @Transactional
    public void deleteVersion(String novelName, String version) {
        String normalizedNovelName = normalizeNovelName(novelName);
        log.info("Logical deleting version: {}/{}", normalizedNovelName, version);
        sceneRepository.deleteVersion(normalizedNovelName, version);
        
        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelName)
                .targetType("VERSION")
                .version(version)
                .status("PENDING")
                .build();
        cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(task.getId())
                .targetId(normalizedNovelName)
                .targetType("VERSION")
                .version(version)
                .build();
        
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ", task.getId());
    }

    @Override
    @Transactional
    public void deleteKnowledgeBase(String novelName) {
        String normalizedNovelName = normalizeNovelName(novelName);
        log.info("Logical deleting knowledge base for: {}", normalizedNovelName);
        sceneRepository.deleteNovel(normalizedNovelName);
        
        CleanupTask task = CleanupTask.builder()
                .targetId(normalizedNovelName)
                .targetType("NOVEL")
                .status("PENDING")
                .build();
        cleanupTaskRepository.save(task);

        CleanupTaskMessage message = CleanupTaskMessage.builder()
                .cleanupTaskId(task.getId())
                .targetId(normalizedNovelName)
                .targetType("NOVEL")
                .build();
        
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ", task.getId());
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
}
