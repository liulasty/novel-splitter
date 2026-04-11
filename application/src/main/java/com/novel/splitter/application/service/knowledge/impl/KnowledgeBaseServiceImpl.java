package com.novel.splitter.application.service.knowledge.impl;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.domain.task.CleanupTaskMessage;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.repository.NovelRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.application.model.dto.SceneSplitProfileDto;
import com.novel.splitter.application.model.dto.VectorPreviewRecordDto;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.embedding.api.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

/**
 * 知识库管理服务实现。
 * <p>删除版本/整书时：先同步删除向量再软删场景行，避免孤儿向量；异步 cleanup 任务仍负责文件等清理，向量删除幂等。</p>
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
    private final TaskService taskService;
    private final VectorStore vectorStore;
    
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
    public Long deleteVersion(String novelName, String version, int chunkSize, int chunkOverlap, boolean purgeTerminalSplitTasks) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        if (taskService.hasActiveTasksForNovelId(novelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Novel has running tasks; cannot delete version right now.");
        }

        log.info("Logical deleting split profile: novelId={} version={} chunk={}/{}", novelId, version, chunkSize, chunkOverlap);
        deleteVectorsForVersionProfile(novelId, normalizedNovelName, version, chunkSize, chunkOverlap);
        sceneRepository.deleteByProfile(novelId, version, chunkSize, chunkOverlap);
        
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
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .build();
        
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ", savedTask.getId());
        maybePurgeTerminalSplitTasks(novelId, version != null ? version.trim() : null, purgeTerminalSplitTasks);
        return savedTask.getId();
    }

    @Override
    @Transactional
    public Long deleteKnowledgeBase(String novelName, boolean purgeTerminalSplitTasks) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        if (taskService.hasActiveTasksForNovelId(novelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Novel has running tasks; cannot delete knowledge base right now.");
        }
        log.info("Logical deleting knowledge base for novelId={} title={}", novelId, normalizedNovelName);
        deleteVectorsForEntireNovel(novelId, normalizedNovelName);
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
        maybePurgeTerminalSplitTasks(novelId, null, purgeTerminalSplitTasks);
        return savedTask.getId();
    }

    @Override
    @Transactional
    public Long deleteKnowledgeBaseById(String novelId, boolean purgeTerminalSplitTasks) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        if (taskService.hasActiveTasksForNovelId(normalizedNovelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Novel has running tasks; cannot delete knowledge base right now.");
        }

        String novelName = novelRepository.findById(normalizedNovelId)
                .map(n -> n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : n.getId())
                .orElse(normalizedNovelId);

        log.info("Logical deleting knowledge base by novelId: {} (name='{}')", normalizedNovelId, novelName);
        deleteVectorsForEntireNovel(normalizedNovelId, novelName);
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
        maybePurgeTerminalSplitTasks(normalizedNovelId, null, purgeTerminalSplitTasks);
        return savedTask.getId();
    }

    @Override
    public List<String> listVersions(String novelName) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        return sceneRepository.listSplitProfilesByNovelId(novelId).stream()
                .map(KnowledgeBaseServiceImpl::toProfileDto)
                .map(SceneSplitProfileDto::getLabel)
                .toList();
    }

    @Override
    public List<String> listVersionsByNovelId(String novelId) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        return sceneRepository.listSplitProfilesByNovelId(normalizedNovelId).stream()
                .map(KnowledgeBaseServiceImpl::toProfileDto)
                .map(SceneSplitProfileDto::getLabel)
                .toList();
    }

    @Override
    public List<SceneSplitProfileDto> listSplitProfilesByNovelId(String novelId) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        return sceneRepository.listSplitProfilesByNovelId(normalizedNovelId).stream()
                .map(KnowledgeBaseServiceImpl::toProfileDto)
                .toList();
    }

    @Override
    @Transactional
    public Long deleteSplitProfileByNovelId(String novelId, String version, int chunkSize, int chunkOverlap, boolean purgeTerminalSplitTasks) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        if (taskService.hasActiveTasksForNovelId(normalizedNovelId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Novel has running tasks; cannot delete version right now.");
        }

        String trimmedVersion = version.trim();
        log.info("Logical deleting split profile by novelId: {} version={} chunk={}/{}", normalizedNovelId, trimmedVersion, chunkSize, chunkOverlap);
        String novelNameForVectors = novelRepository.findById(normalizedNovelId)
                .map(n -> n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : n.getId())
                .orElse(normalizedNovelId);
        deleteVectorsForVersionProfile(normalizedNovelId, novelNameForVectors, trimmedVersion, chunkSize, chunkOverlap);
        sceneRepository.deleteByProfile(normalizedNovelId, trimmedVersion, chunkSize, chunkOverlap);

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
                .novelName(novelNameForVectors)
                .version(trimmedVersion)
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .build();

        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        log.info("Sent cleanup task {} to MQ for novelId {} profile {}", savedTask.getId(), normalizedNovelId, trimmedVersion);
        maybePurgeTerminalSplitTasks(normalizedNovelId, trimmedVersion, purgeTerminalSplitTasks);
        return savedTask.getId();
    }

    /**
     * 软删场景行之前同步删掉 Chroma 中对应向量，避免出现「DB 已软删、向量仍在」的孤儿向量。
     * 过滤条件与 {@link com.novel.splitter.application.worker.CleanupWorker} 一致；后续 cleanup 队列中的删除为幂等。
     */
    private void deleteVectorsForVersionProfile(
            String novelId, String novelNameForLegacyMetadata, String version, int chunkSize, int chunkOverlap) {
        String ver = version != null ? version.trim() : "";
        if (ver.isEmpty()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        try {
            vectorStore.delete(Map.of(
                    "novelId", novelId,
                    "version", ver,
                    "chunkSize", chunkSize,
                    "chunkOverlap", chunkOverlap));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to delete vectors before soft-deleting scenes: novelId=" + novelId + " version=" + ver
                            + " chunk=" + chunkSize + "/" + chunkOverlap,
                    e);
        }
        if (novelNameForLegacyMetadata != null && !novelNameForLegacyMetadata.isBlank()) {
            try {
                vectorStore.delete(Map.of(
                        "novel", novelNameForLegacyMetadata,
                        "version", ver,
                        "chunkSize", chunkSize,
                        "chunkOverlap", chunkOverlap));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to delete vectors (legacy novel metadata) before soft-deleting scenes: novel="
                                + novelNameForLegacyMetadata + " version=" + ver,
                        e);
            }
        }
    }

    private void deleteVectorsForEntireNovel(String novelId, String novelNameForLegacyMetadata) {
        try {
            vectorStore.delete(Map.of("novelId", novelId));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete vectors before soft-deleting novel scenes: novelId=" + novelId, e);
        }
        if (novelNameForLegacyMetadata != null && !novelNameForLegacyMetadata.isBlank()) {
            try {
                vectorStore.delete(Map.of("novel", novelNameForLegacyMetadata));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "Failed to delete vectors (legacy novel metadata) before soft-deleting novel: novel="
                                + novelNameForLegacyMetadata,
                        e);
            }
        }
    }

    private void maybePurgeTerminalSplitTasks(String novelId, String versionOrNull, boolean purge) {
        if (!purge) {
            return;
        }
        int removed = versionOrNull == null || versionOrNull.isEmpty()
                ? taskService.purgeTerminalSplitTasksForNovel(novelId)
                : taskService.purgeTerminalSplitTasksForNovelAndVersion(novelId, versionOrNull);
        log.info("purgeTerminalSplitTasks removed {} rows for novelId={} versionFilter={}", removed, novelId, versionOrNull);
    }

    private static SceneSplitProfileDto toProfileDto(SceneSplitProfile p) {
        return SceneSplitProfileDto.builder()
                .version(p.version())
                .chunkSize(p.chunkSize())
                .chunkOverlap(p.chunkOverlap())
                .build();
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
