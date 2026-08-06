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
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.application.model.dto.SceneSplitProfileDto;
import com.novel.splitter.application.model.dto.VectorPreviewRecordDto;
import com.novel.splitter.domain.model.SceneSplitProfile;
import com.novel.splitter.application.service.knowledge.CleanupTaskCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 知识库管理服务实现。
 * <p>删除版本/整书时：同步软删场景行（DB 快），向量与文件删除由 CleanupWorker 异步执行（事务提交后发 MQ）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final SceneRepository sceneRepository;
    private final NovelRepository novelRepository;
    private final NovelVersionRepository novelVersionRepository;
    private final CleanupTaskRepository cleanupTaskRepository;
    private final RabbitTemplate rabbitTemplate;
    private final DtoMapper dtoMapper;
    private final TaskService taskService;
    private final ApplicationEventPublisher applicationEventPublisher;
    
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
                return scene.getChapterTitle(); // 复用 chapterTitle 作为 type 的 hack，与 JpaImpl 保持一致
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
    public List<SceneDto> getScenesByNovel(String novelName, String version) {
        String normalizedNovelName = normalizeNovelName(novelName);
        String novelId = novelRepository.findByTitle(normalizedNovelName)
                .map(n -> n.getId())
                .orElseThrow(() -> new IllegalArgumentException("novel not found by title: " + normalizedNovelName));
        if (version == null || version.isBlank()) {
            return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelId(novelId));
        }
        return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelIdAndVersion(novelId, version.trim()));
    }

    @Override
    public List<SceneDto> getScenesByNovelId(String novelId, String version) {
        String normalizedNovelId = novelId != null ? novelId.trim() : null;
        if (normalizedNovelId == null || normalizedNovelId.isEmpty()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        if (version == null || version.isBlank()) {
            return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelId(normalizedNovelId));
        }
        return dtoMapper.toSceneDtos(sceneRepository.findAllByNovelIdAndVersion(normalizedNovelId, version.trim()));
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

        log.info("逻辑删除切分数据集: novelId={} version={} chunk={}/{}", novelId, version, chunkSize, chunkOverlap);
        sceneRepository.deleteByProfile(novelId, version, chunkSize, chunkOverlap);
        if (version != null && !version.isBlank()) {
            novelVersionRepository.delete(novelId, version.trim());
        }

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
        
        applicationEventPublisher.publishEvent(new CleanupTaskCreatedEvent(message));
        log.info("已发布清理事件, cleanup task={}", savedTask.getId());
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
        log.info("逻辑删除知识库: novelId={} title={}", novelId, normalizedNovelName);
        List<String> versionCollections = collectVersionCollections(novelId);
        sceneRepository.deleteNovelById(novelId);
        novelVersionRepository.deleteByNovelId(novelId);

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
                .collectionNames(versionCollections)
                .build();
        
        applicationEventPublisher.publishEvent(new CleanupTaskCreatedEvent(message));
        log.info("已发布清理事件, cleanup task={}", savedTask.getId());
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

        log.info("按 novelId 逻辑删除知识库: {} (name='{}')", normalizedNovelId, novelName);
        List<String> versionCollections = collectVersionCollections(normalizedNovelId);
        sceneRepository.deleteNovelById(normalizedNovelId);
        novelVersionRepository.deleteByNovelId(normalizedNovelId);

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
                .collectionNames(versionCollections)
                .build();

        applicationEventPublisher.publishEvent(new CleanupTaskCreatedEvent(message));
        log.info("已发布清理事件, cleanup task={}, novelId={}", savedTask.getId(), normalizedNovelId);
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
        log.info("按 novelId 逻辑删除切分数据集: {} version={} chunk={}/{}", normalizedNovelId, trimmedVersion, chunkSize, chunkOverlap);
        String novelNameForVectors = novelRepository.findById(normalizedNovelId)
                .map(n -> n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : n.getId())
                .orElse(normalizedNovelId);
        sceneRepository.deleteByProfile(normalizedNovelId, trimmedVersion, chunkSize, chunkOverlap);
        novelVersionRepository.delete(normalizedNovelId, trimmedVersion);

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

        applicationEventPublisher.publishEvent(new CleanupTaskCreatedEvent(message));
        log.info("已发布清理事件, cleanup task={}, novelId={}, profile={}", savedTask.getId(), normalizedNovelId, trimmedVersion);
        maybePurgeTerminalSplitTasks(normalizedNovelId, trimmedVersion, purgeTerminalSplitTasks);
        return savedTask.getId();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCleanupTaskCreated(CleanupTaskCreatedEvent event) {
        CleanupTaskMessage message = event.getMessage();
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", message);
        } catch (Exception e) {
            log.error("事务提交后发送清理任务 {} 到 MQ 失败", message.getCleanupTaskId(), e);
            cleanupTaskRepository.findById(message.getCleanupTaskId()).ifPresent(task -> {
                task.setStatus("FAILED");
                task.setErrorMessage("MQ send failed: " + e.getMessage());
                cleanupTaskRepository.save(task);
            });
        }
    }

    private void maybePurgeTerminalSplitTasks(String novelId, String versionOrNull, boolean purge) {
        if (!purge) {
            return;
        }
        int removed = versionOrNull == null || versionOrNull.isEmpty()
                ? taskService.purgeTerminalSplitTasksForNovel(novelId)
                : taskService.purgeTerminalSplitTasksForNovelAndVersion(novelId, versionOrNull);
        log.info("purgeTerminalSplitTasks 清理了 {} 行, novelId={}, versionFilter={}", removed, novelId, versionOrNull);
    }

    private static SceneSplitProfileDto toProfileDto(SceneSplitProfile p) {
        return SceneSplitProfileDto.builder()
                .version(p.version())
                .chunkSize(p.chunkSize())
                .chunkOverlap(p.chunkOverlap())
                .build();
    }

    /**
     * 删除版本行之前捕获该小说全部版本集合名，供异步整书清理按集合整删。
     * <p>版本行在 deleteByNovelId 后已消失，无法在 CleanupWorker 阶段再枚举，因此删除时快照进消息。</p>
     */
    private List<String> collectVersionCollections(String novelId) {
        return novelVersionRepository.findByNovelId(novelId).stream()
                .map(v -> VectorStore.collectionNameFor(novelId, v.getVersionTag()))
                .toList();
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
