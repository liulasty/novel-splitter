package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.NovelSummaryListScope;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.LoadNovelRequestDto;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.NovelPipelineRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.model.dto.NovelSummaryDto;
import com.novel.splitter.application.model.dto.CreateVersionRequest;
import com.novel.splitter.application.model.dto.NovelVersionDto;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.knowledge.KnowledgeBaseService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.orchestration.EmbedPipelineOrchestrator;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.RecognitionStrategyType;
import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.domain.repository.NovelVersionRepository;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.repository.ChapterRepository;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.repository.SceneRepository;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.SceneSplitProfileDto;
import com.novel.splitter.application.model.dto.ReparseChaptersRequestDto;
import com.novel.splitter.application.model.dto.SceneSplitRequestDto;
import com.novel.splitter.application.model.dto.SplitRetryRequestDto;
import com.novel.splitter.core.ChapterRecognizer;
import com.novel.splitter.domain.task.SplitTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * 小说侧编排门面：上传、Load、场景切分、向量化、流水线等。
 * <p>
 * <strong>场景切分与小说状态：</strong>当 {@link NovelStatus#EMBEDDING} 时禁止再投递 Split 队列（会删场景与向量侧数据），
 * 统一在 {@link #startSceneSplitTask} 入口校验，返回 HTTP 409 Conflict，避免与向量化并发读写冲突。
 * （是否同时拦截 {@link NovelStatus#SPLITTING} 可后续与产品约定，一期仅拦 EMBEDDING。）
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NovelFacadeServiceImpl implements NovelFacadeService {

    private static final String DEFAULT_VERSION = "v1";
    private static final SplitStrategy DEFAULT_SPLIT_STRATEGY = SplitStrategy.OVERLAP_CHUNK;

    /** 与 spring.servlet.multipart.max-file-size 保持一致，避免业务校验与容器限制不一致 */
    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private DataSize maxUploadFileSize;

    /** 与 SplitNovelUseCase 的 splitter.ingestion.chunk-size 保持一致，作为版本默认滑窗大小 */
    @Value("${splitter.ingestion.chunk-size:350}")
    private int defaultChunkSize;

    /** 与 SplitNovelUseCase 的 splitter.ingestion.chunk-overlap 保持一致，作为版本默认重叠 */
    @Value("${splitter.ingestion.chunk-overlap:65}")
    private int defaultChunkOverlap;

    private final NovelStorageService novelStorageService;
    private final NovelService novelService;
    private final ChapterService chapterService;
    private final NovelCacheRepository novelCacheRepository;
    private final TaskService taskService;
    private final TaskQueuePort taskQueuePort;
    private final EmbedPipelineOrchestrator embedPipelineOrchestrator;
    private final DownloadService downloadService;
    private final SceneRepository sceneRepository;
    private final ChapterRepository chapterRepository;
    private final DtoMapper dtoMapper;
    private final NovelVersionRepository novelVersionRepository;
    private final NovelVersionService novelVersionService;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public List<NovelStatRecordDto> getNovelStats() {
        List<SceneCountByProfile> sceneCounts = sceneRepository.countScenesByNovelVersionAndChunk();

        // novelId -> (profile label -> scene count)
        Map<String, Map<String, Long>> novelProfileCounts = new HashMap<>();
        for (SceneCountByProfile row : sceneCounts) {
            String novelId = row.novelId();
            String version = row.version();
            Integer chunkSize = row.chunkSize();
            Integer chunkOverlap = row.chunkOverlap();
            long count = row.sceneCount() != null ? row.sceneCount() : 0L;
            String label = SceneSplitProfileDto.builder()
                    .version(version)
                    .chunkSize(chunkSize)
                    .chunkOverlap(chunkOverlap)
                    .build()
                    .getLabel();
            novelProfileCounts.computeIfAbsent(novelId, k -> new HashMap<>()).merge(label, count, Long::sum);
        }

        List<SplitTask> allTasks = taskService.getAllTasks();
        // Group tasks by novelId
        Map<String, List<SplitTask>> tasksByNovel = allTasks.stream()
                .filter(t -> t.getNovelId() != null)
                .collect(Collectors.groupingBy(SplitTask::getNovelId));

        // 宽容标题查找：软删/不存在的小说不在 listNovels()（@SQLRestriction is_deleted=false），
        // 用 map 兜底避免 getNovelById 对已删小说抛 "Novel not found" 导致整个 stats 端点 400。
        Map<String, String> novelTitles = new HashMap<>();
        for (Novel n : novelService.listNovels()) {
            String id = n.getId();
            if (id == null) {
                continue;
            }
            novelTitles.put(id, n.getTitle() != null && !n.getTitle().isBlank() ? n.getTitle() : id);
        }

        List<NovelStatRecordDto> stats = new ArrayList<>();

        // Merge data
        for (Map.Entry<String, Map<String, Long>> entry : novelProfileCounts.entrySet()) {
            String novelId = entry.getKey();
            Map<String, Long> profileMap = entry.getValue();

            List<String> versions = new ArrayList<>(profileMap.keySet());
            Collections.sort(versions);
            long totalScenes = profileMap.values().stream().mapToLong(Long::longValue).sum();

            // Get latest task for this novel
            List<SplitTask> novelTasks = tasksByNovel.getOrDefault(novelId, Collections.emptyList());
            SplitTask latestTask = novelTasks.stream()
                    .max(Comparator.comparing(SplitTask::getCreatedAt))
                    .orElse(null);

            String ingestTime = null;
            String status = "UNKNOWN";

            if (latestTask != null) {
                LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(latestTask.getCreatedAt()), ZoneId.systemDefault());
                ingestTime = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                status = latestTask.getStatus() != null ? latestTask.getStatus().name() : "UNKNOWN";
            }

            String titleForDisplay = novelTitles.getOrDefault(novelId, novelId);

            NovelStatRecordDto dto = NovelStatRecordDto.builder()
                    .novelId(novelId)
                    .novelName(titleForDisplay)
                    .versions(versions)
                    .sceneCount(totalScenes)
                    .vectorCount(totalScenes) // Assume 1 scene = 1 vector
                    .ingestTime(ingestTime)
                    .status(status)
                    .build();
            stats.add(dto);
        }

        // Add novels that have tasks but no scenes yet
        for (Map.Entry<String, List<SplitTask>> entry : tasksByNovel.entrySet()) {
            String novelId = entry.getKey();
            if (!novelTitles.containsKey(novelId)) {
                continue; // 软删/孤儿任务引用的小说：跳过，不出现在 stats 中
            }
            if (!novelProfileCounts.containsKey(novelId)) {
                SplitTask latestTask = entry.getValue().stream()
                        .max(Comparator.comparing(SplitTask::getCreatedAt))
                        .orElse(null);

                String ingestTime = null;
                String status = "UNKNOWN";
                if (latestTask != null) {
                    LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(latestTask.getCreatedAt()), ZoneId.systemDefault());
                    ingestTime = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    status = latestTask.getStatus() != null ? latestTask.getStatus().name() : "UNKNOWN";
                }

                String titleForDisplay = novelTitles.getOrDefault(novelId, novelId);
                NovelStatRecordDto dto = NovelStatRecordDto.builder()
                        .novelId(novelId)
                        .novelName(titleForDisplay)
                        .versions(Collections.emptyList())
                        .sceneCount(0)
                        .vectorCount(0)
                        .ingestTime(ingestTime)
                        .status(status)
                        .build();
                stats.add(dto);
            }
        }

        return stats;
    }

    @Override
    public List<String> listNovels() throws IOException {
        return novelStorageService.listNovels();
    }

    @Override
    public List<NovelSummaryDto> listNovelSummaries(NovelSummaryListScope scope) {
        NovelSummaryListScope s = scope != null ? scope : NovelSummaryListScope.ALL;
        return novelService.listNovels().stream()
                .filter(n -> n != null && !n.isDeleted())
                .filter(n -> s != NovelSummaryListScope.EMBED_READY || n.getStatus() == NovelStatus.COMPLETED)
                .map(n -> NovelSummaryDto.builder()
                        .novelId(n.getId())
                        .title(n.getTitle())
                        .author(n.getAuthor())
                        .status(n.getStatus() != null ? n.getStatus().name() : null)
                        .filePath(n.getFilePath())
                        .createdAt(n.getCreatedAt())
                        .updatedAt(n.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public NovelUploadResponseDto uploadNovel(UploadNovelCommand command) throws IOException {
        if (command == null || command.content() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upload content is required");
        }
        String name = command.originalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 .txt 文件");
        }
        if (command.sizeBytes() == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        if (command.sizeBytes() > 0 && command.sizeBytes() > maxUploadFileSize.toBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件过大，最大允许 " + maxUploadFileSize);
        }
        String novelId = novelService.createNovel(command.content(), command.originalFilename(), command.title(), command.author(), command.description());
        return NovelUploadResponseDto.builder()
                .message("文件上传成功")
                .novelId(novelId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto split(String novelId, IngestRequest request) throws IOException {
        log.info("接收到章节解析请求: novelId={}, request={}", novelId, request);
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        novelService.getNovelById(id);
        int maxScenes = request != null && request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request != null ? request.getVersion() : null);
        TaskSubmitResponseDto dto = startChapterParseTask(
                id, version, maxScenes, false,
                request != null ? request.getChapterTitleRegex() : null,
                request != null ? request.getStrategy() : null);
        log.info("章节解析任务已投递 Load 队列, taskId={}", dto.getTaskId());
        return dto;
    }

    @Override
    public TaskSubmitResponseDto sceneSplit(String novelId, SceneSplitRequestDto request) throws IOException {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        novelService.getNovelById(id);
        assertStructuredArtifactsReady(id);
        int maxScenes = request != null && request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request != null ? request.getVersion() : null);
        boolean triggerEmbed = request != null && request.isTriggerEmbed();
        TaskSubmitResponseDto dto = startSceneSplitTask(
                id,
                maxScenes,
                version,
                triggerEmbed,
                request != null ? request.getChunkSize() : null,
                request != null ? request.getChunkOverlap() : null);
        log.info("场景切分任务已投递 Split 队列, taskId={}", dto.getTaskId());
        return dto;
    }

    @Override
    public TaskSubmitResponseDto retrySplit(String novelId, SplitRetryRequestDto request) throws IOException {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        novelService.getNovelById(id);

        assertStructuredArtifactsReady(id);

        int maxScenes = (request != null && request.getMaxScenes() > 0) ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request != null ? request.getVersion() : null);
        boolean triggerEmbed = request != null && request.isTriggerEmbed();

        TaskSubmitResponseDto dto = startSceneSplitTask(
                id,
                maxScenes,
                version,
                triggerEmbed,
                request != null ? request.getChunkSize() : null,
                request != null ? request.getChunkOverlap() : null);
        dto.setMessage("场景切分重试已提交到队列（跳过章节解析）");
        log.info("Sent taskId {} to split queue for retrySplit", dto.getTaskId());
        return dto;
    }

    @Override
    public TaskSubmitResponseDto load(String novelId, LoadNovelRequestDto request) throws IOException {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        String version = normalizeVersion(request != null ? request.getVersion() : null);
        boolean force = request != null && request.isForce();
        ensureChapterTitleRegexValid(request != null ? request.getChapterTitleRegex() : null);
        ensureRecognitionStrategyValid(request != null ? request.getStrategy() : null);

        String taskId = UUID.randomUUID().toString();
        taskService.createTaskWithNovelAdmission(taskId, TaskType.LOAD, id, 0, version);
        SplitTaskMessage message = new SplitTaskMessage(taskId, id, 0, version, false);
        message.setForceReload(force);
        message.setChapterTitleRegex(trimToNull(request != null ? request.getChapterTitleRegex() : null));
        message.setRecognitionStrategy(request != null ? request.getStrategy() : null);
        message.setTaskTypeForRecovery(TaskType.LOAD.name());
        taskQueuePort.sendLoad(message);
        log.info("Sent taskId {} to load queue (standalone LOAD, strategy={})", taskId,
                request != null ? request.getStrategy() : "CN_CHAPTER");

        return TaskSubmitResponseDto.builder()
                .message("Load 任务已提交到队列")
                .taskId(taskId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto embed(String novelId) throws IOException {
        return embed(novelId, null, null, null);
    }

    @Override
    public TaskSubmitResponseDto embed(String novelId, String version) throws IOException {
        return embed(novelId, version, null, null);
    }

    @Override
    public TaskSubmitResponseDto embed(String novelId, String version, Integer chunkSize, Integer chunkOverlap)
            throws IOException {
        log.info("接收到向量化请求: novelId={}, version={}, chunkSize={}, chunkOverlap={}",
                novelId, version, chunkSize, chunkOverlap);

        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String nid = novelId.trim();
        String v = normalizeVersion(version);

        String taskId = UUID.randomUUID().toString();
        taskService.createEmbedTaskWithNovelAdmission(taskId, nid, v, chunkSize, chunkOverlap);

        embedPipelineOrchestrator.startNewEmbedRun(taskId, nid, v, chunkSize, chunkOverlap);
        log.info("Embed orchestration started taskId {}", taskId);

        return TaskSubmitResponseDto.builder()
                .message("向量化任务已提交（编排已启动）")
                .taskId(taskId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto pipeline(String novelId, NovelPipelineRequestDto request) throws IOException {
        if (request == null || request.getStages() == null || request.getStages().isEmpty()) {
            throw new IllegalArgumentException("stages is required");
        }
        boolean hasSplit = request.getStages().stream()
                .filter(stage -> stage != null && !stage.isBlank())
                .map(stage -> stage.trim().toUpperCase(Locale.ROOT))
                .anyMatch("SPLIT"::equals);
        boolean hasEmbed = request.getStages().stream()
                .filter(stage -> stage != null && !stage.isBlank())
                .map(stage -> stage.trim().toUpperCase(Locale.ROOT))
                .anyMatch("EMBED"::equals);

        if (!hasSplit && !hasEmbed) {
            throw new IllegalArgumentException("Unsupported stages. Allowed values: SPLIT, EMBED");
        }

        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        novelService.getNovelById(id);

        if (hasSplit) {
            int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
            String version = normalizeVersion(request.getVersion());
            String entry = normalizeSplitEntry(request.getSplitEntry());

            switch (entry) {
                case "SCENE_ONLY":
                    assertStructuredArtifactsReady(id);
                    TaskSubmitResponseDto sceneTask = startSceneSplitTask(
                            id, maxScenes, version, hasEmbed,
                            request.getChunkSize(), request.getChunkOverlap());
                    sceneTask.setMessage(hasEmbed
                            ? "已提交：场景切分 → 向量化（串联）"
                            : "已提交：场景切分（跳过章节解析）");
                    return sceneTask;
                case "CHAPTER_RELOAD": {
                    TaskSubmitResponseDto chapterTask = startChapterParseTask(
                            id, version, maxScenes, true, request.getChapterTitleRegex(),
                                    request.getStrategy());
                    if (hasEmbed) {
                        chapterTask.setMessage(chapterTask.getMessage()
                                + "。向量化请在场景切分完成后触发 EMBED，或使用 POST /scene-split 且 triggerEmbed=true。");
                    }
                    return chapterTask;
                }
                default: {
                    TaskSubmitResponseDto chapterTask = startChapterParseTask(
                            id, version, maxScenes, false, request.getChapterTitleRegex(),
                                    request.getStrategy());
                    if (hasEmbed) {
                        chapterTask.setMessage(chapterTask.getMessage()
                                + "。完整流水线：解析完成后请调用 POST /scene-split（可 triggerEmbed 串联向量化）。");
                    }
                    return chapterTask;
                }
            }
        }

        return embed(id, request.getVersion());
    }

    @Override
    public TaskSubmitResponseDto ingest(IngestRequest request) throws IOException {
        // 保留原有的 ingest 作为向前兼容，或者直接复用
        log.info("接收到原 ingest 请求: {}", request);
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }

        novelStorageService.resolveExistingNovelPath(request.getFileName());
        ensureChapterTitleRegexValid(request.getChapterTitleRegex());
        String taskId = UUID.randomUUID().toString();
        String novelId = normalizeNovelId(request.getFileName());
        int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request.getVersion());

        taskService.createTask(taskId, TaskType.CHAPTER_PARSE, novelId, request.getFileName(), maxScenes, version);

        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version);
        message.setTaskTypeForRecovery(TaskType.CHAPTER_PARSE.name());
        message.setChapterTitleRegex(trimToNull(request.getChapterTitleRegex()));
        taskQueuePort.sendLoad(message);
        log.info("Sent taskId {} to load queue", taskId);

        log.info("入库任务已发送到队列, taskId: {}", taskId);
        return TaskSubmitResponseDto.builder()
                .message("入库任务已提交到队列")
                .taskId(taskId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto reparseChapters(String novelId, ReparseChaptersRequestDto request) throws IOException {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        novelService.getNovelById(id);
        int maxScenes = request != null && request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request != null ? request.getVersion() : null);
        return startChapterParseTask(
                id, version, maxScenes, true,
                request != null ? request.getChapterTitleRegex() : null,
                request != null ? request.getStrategy() : null);
    }

    @Override
    public TaskSubmitResponseDto downloadAndIngest(DownloadAndIngestRequest request) throws IOException {
        log.info("接收到下载并入库请求: url={}, name={}", request.getUrl(), request.getName());
        
        // 1. 同步下载，得到落盘文件名
        String relativePath = downloadService.downloadNovel(request.getUrl(), request.getName());

        // 2. 下载后先创建 Novel 资源，再统一走 novelId 驱动 pipeline
        String novelId = novelService.createNovelFromStoredFile(relativePath, request.getName(), null, "downloaded from " + request.getUrl());

        NovelPipelineRequestDto pipelineRequest = new NovelPipelineRequestDto();
        pipelineRequest.setVersion(request.getVersion());
        pipelineRequest.setMaxScenes(request.getMaxScenes());
        pipelineRequest.setSplitEntry(request.getSplitEntry());
        pipelineRequest.setChunkSize(request.getChunkSize());
        pipelineRequest.setChunkOverlap(request.getChunkOverlap());
        pipelineRequest.setChapterTitleRegex(request.getChapterTitleRegex());
        if (request.getStages() == null || request.getStages().isEmpty()) {
            pipelineRequest.setStages(List.of("SPLIT", "EMBED"));
        } else {
            pipelineRequest.setStages(request.getStages());
        }
        return pipeline(novelId, pipelineRequest);
    }

    @Override
    public List<com.novel.splitter.application.model.dto.ChapterDto> getChapters(String novelId) {
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }
        novel.checkCanReadChapters();
        return dtoMapper.toChapterDtos(chapterService.getChaptersByNovelId(novelId));
    }

    @Override
    public PagedResult<com.novel.splitter.application.model.dto.SceneDto> getScenesByChapter(String novelId, Long chapterId, String version, int page, int size) {
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }
        novel.checkCanReadChapters();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        if (version == null || version.isBlank()) {
            return sceneRepository
                    .findByNovelIdAndChapterId(novelId, chapterId, PageQuery.of(safePage, safeSize))
                    .map(dtoMapper::toSceneDto);
        }
        return sceneRepository
                .findByNovelIdAndChapterIdAndVersion(novelId, chapterId, version.trim(), PageQuery.of(safePage, safeSize))
                .map(dtoMapper::toSceneDto);
    }

    @Override
    public void softDeleteNovel(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        taskService.ensureNoActiveTasksForNovelLocked(id, "Novel has running tasks; cannot delete right now.");
        // Soft delete novel row first; also soft delete chapters/scenes for visibility.
        novelService.softDeleteNovel(id);
        chapterRepository.deleteByNovelId(id);
        sceneRepository.deleteNovelById(id);
        // 级联删除该小说全部版本行（含版本专属向量集合由异步清理回收）
        novelVersionRepository.deleteByNovelId(id);
    }

    @Override
    public List<NovelVersionDto> listVersions(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        Novel novel = novelService.getNovelById(id);
        String activeTag = novel != null ? novel.getActiveVersionTag() : null;
        return novelVersionRepository.findByNovelId(id).stream()
                .map(v -> toVersionDto(v, activeTag))
                .collect(Collectors.toList());
    }

    @Override
    public NovelVersionDto createVersion(String novelId, CreateVersionRequest request) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        Novel novel = novelService.getNovelById(id);

        String versionTag = trimToNull(request != null ? request.getVersionTag() : null);
        if (versionTag == null) {
            versionTag = nextVersionTag(id);
        }

        SplitStrategy strategy = parseSplitStrategy(request != null ? request.getSplitStrategy() : null);

        int chunkSize = resolveDefaultChunkSize(request != null ? request.getChunkSize() : null);
        int chunkOverlap = resolveDefaultChunkOverlap(request != null ? request.getChunkOverlap() : null);
        if (chunkOverlap >= chunkSize) {
            chunkOverlap = Math.max(0, chunkSize - 1);
        }

        long now = System.currentTimeMillis();
        NovelVersion version = NovelVersion.builder()
                .novelId(id)
                .versionTag(versionTag)
                .splitStrategy(strategy)
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .status(VersionStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        novelVersionRepository.save(version);
        log.info("Created version {}/{} strategy={} chunk={}/{}", id, versionTag, strategy, chunkSize, chunkOverlap);
        return toVersionDto(version, novel != null ? novel.getActiveVersionTag() : null);
    }

    @Override
    public TaskSubmitResponseDto startVersionSplit(String novelId, String versionTag) throws IOException {
        if (novelId == null || novelId.isBlank() || versionTag == null || versionTag.isBlank()) {
            throw new IllegalArgumentException("novelId and versionTag must not be blank");
        }
        String id = novelId.trim();
        String tag = versionTag.trim();
        NovelVersion version = novelVersionRepository.findById(id, tag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在: " + tag));
        TaskSubmitResponseDto dto = startSceneSplitTask(
                id, Integer.MAX_VALUE, tag, false,
                version.getChunkSize(), version.getChunkOverlap());
        log.info("版本切分任务已投递 Split 队列, novelId={}, version={}, taskId={}", id, tag, dto.getTaskId());
        return dto;
    }

    @Override
    public TaskSubmitResponseDto startVersionEmbed(String novelId, String versionTag) throws IOException {
        if (novelId == null || novelId.isBlank() || versionTag == null || versionTag.isBlank()) {
            throw new IllegalArgumentException("novelId and versionTag must not be blank");
        }
        String id = novelId.trim();
        String tag = versionTag.trim();
        NovelVersion version = novelVersionRepository.findById(id, tag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在: " + tag));
        TaskSubmitResponseDto dto = embed(id, tag, version.getChunkSize(), version.getChunkOverlap());
        log.info("版本向量化编排已启动, novelId={}, version={}, taskId={}", id, tag, dto.getTaskId());
        return dto;
    }

    @Override
    public void activateVersion(String novelId, String versionTag) {
        if (novelId == null || novelId.isBlank() || versionTag == null || versionTag.isBlank()) {
            throw new IllegalArgumentException("novelId and versionTag must not be blank");
        }
        novelVersionService.activate(novelId.trim(), versionTag.trim());
    }

    @Override
    public void deleteVersion(String novelId, String versionTag) {
        if (novelId == null || novelId.isBlank() || versionTag == null || versionTag.isBlank()) {
            throw new IllegalArgumentException("novelId and versionTag must not be blank");
        }
        String id = novelId.trim();
        String tag = versionTag.trim();
        NovelVersion version = novelVersionRepository.findById(id, tag).orElse(null);
        int chunkSize = version != null && version.getChunkSize() != null ? version.getChunkSize() : 0;
        int chunkOverlap = version != null && version.getChunkOverlap() != null ? version.getChunkOverlap() : 0;
        // 删除该版本切分数据集/向量（同步软删场景 + 异步清理向量与文件）
        knowledgeBaseService.deleteSplitProfileByNovelId(id, tag, chunkSize, chunkOverlap, false);
        // 兜底删除 novel_version 行（现有删除流程不覆盖该表）
        novelVersionRepository.delete(id, tag);
        log.info("Deleted version {}/{}", id, tag);
    }

    @Override
    public TaskSubmitResponseDto baselineParse(String novelId, ReparseChaptersRequestDto request) throws IOException {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        return reparseChapters(novelId.trim(), request != null ? request : new ReparseChaptersRequestDto());
    }

    private String nextVersionTag(String novelId) {
        int maxNum = 0;
        for (NovelVersion v : novelVersionRepository.findByNovelId(novelId)) {
            String tag = v.getVersionTag();
            if (tag != null && tag.matches("v\\d+")) {
                maxNum = Math.max(maxNum, Integer.parseInt(tag.substring(1)));
            }
        }
        return "v" + (maxNum + 1);
    }

    private static SplitStrategy parseSplitStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SPLIT_STRATEGY;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return SplitStrategy.valueOf(u);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法 splitStrategy: " + raw);
        }
    }

    private int resolveDefaultChunkSize(Integer chunkSize) {
        return chunkSize != null && chunkSize > 0 ? chunkSize : defaultChunkSize;
    }

    private int resolveDefaultChunkOverlap(Integer chunkOverlap) {
        return chunkOverlap != null && chunkOverlap >= 0 ? chunkOverlap : defaultChunkOverlap;
    }

    private static NovelVersionDto toVersionDto(NovelVersion v, String activeTag) {
        return NovelVersionDto.builder()
                .novelId(v.getNovelId())
                .versionTag(v.getVersionTag())
                .splitStrategy(v.getSplitStrategy() != null ? v.getSplitStrategy().name() : null)
                .chunkSize(v.getChunkSize())
                .chunkOverlap(v.getChunkOverlap())
                .status(v.getStatus() != null ? v.getStatus().name() : null)
                .splitCursorChapterIndex(v.getSplitCursorChapterIndex())
                .splitCursorSceneSeq(v.getSplitCursorSceneSeq())
                .embedRunId(v.getEmbedRunId())
                .embedCursorSceneSeq(v.getEmbedCursorSceneSeq())
                .collectionName(v.getCollectionName())
                .activatedAt(v.getActivatedAt())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .active(activeTag != null && activeTag.equals(v.getVersionTag()))
                .build();
    }

    private void applyChunkingParams(SplitTaskMessage message, Integer chunkSize, Integer chunkOverlap) {
        if (message == null) {
            return;
        }
        if (chunkSize != null && chunkSize > 0) {
            message.setChunkSize(chunkSize);
        }
        if (chunkOverlap != null && chunkOverlap >= 0) {
            message.setChunkOverlap(chunkOverlap);
        }
    }

    private String normalizeSplitEntry(String raw) {
        if (raw == null || raw.isBlank()) {
            return "FULL";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if ("FULL".equals(u) || "CHAPTER_RELOAD".equals(u) || "SCENE_ONLY".equals(u)) {
            return u;
        }
        throw new IllegalArgumentException("splitEntry 必须是 FULL、CHAPTER_RELOAD、SCENE_ONLY 之一");
    }

    private void assertStructuredArtifactsReady(String novelId) throws IOException {
        boolean hasDbChapters = chapterService.hasChapters(novelId);
        boolean hasParsedFiles = false;
        try (java.util.stream.Stream<java.nio.file.Path> s = novelCacheRepository.listChapterFiles(novelId)) {
            hasParsedFiles = s.findAny().isPresent();
        } catch (Exception e) {
            hasParsedFiles = false;
        }
        if (!hasDbChapters || !hasParsedFiles) {
            throw new IllegalStateException(
                    "需要已完成章节结构化（DB chapters + 解析 JSON）。hasDbChapters=" + hasDbChapters + ", hasParsedFiles=" + hasParsedFiles);
        }
    }

    /**
     * 章节解析 → Load 队列（完成后不自动场景切分）。
     */
    private TaskSubmitResponseDto startChapterParseTask(
            String novelId, String version, int maxScenes, boolean forceReload,
            String chapterTitleRegex, String strategy)
            throws IOException {
        ensureChapterTitleRegexValid(chapterTitleRegex);
        ensureRecognitionStrategyValid(strategy);
        String taskId = UUID.randomUUID().toString();
        taskService.createTaskWithNovelAdmission(taskId, TaskType.CHAPTER_PARSE, novelId, maxScenes, version);
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version, false);
        message.setTaskTypeForRecovery(TaskType.CHAPTER_PARSE.name());
        message.setForceReload(forceReload);
        message.setChapterTitleRegex(trimToNull(chapterTitleRegex));
        message.setRecognitionStrategy(strategy);
        taskQueuePort.sendLoad(message);
        return TaskSubmitResponseDto.builder()
                .taskId(taskId)
                .message(forceReload ? "章节重解析任务已提交（Load 队列）" : "章节解析任务已提交（Load 队列）")
                .build();
    }

    /**
     * 场景切分 → Split 队列；triggerEmbed 时任务类型记为 PIPELINE 以便运维区分。
     */
    private TaskSubmitResponseDto startSceneSplitTask(
            String novelId,
            int maxScenes,
            String version,
            boolean triggerEmbed,
            Integer chunkSize,
            Integer chunkOverlap) throws IOException {
        assertSceneSplitAllowed(novelId);
        String taskId = UUID.randomUUID().toString();
        TaskType recordType = triggerEmbed ? TaskType.PIPELINE : TaskType.SCENE_SPLIT;
        taskService.createTaskWithNovelAdmission(taskId, recordType, novelId, maxScenes, version);
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version, triggerEmbed);
        message.setTaskTypeForRecovery(recordType.name());
        applyChunkingParams(message, chunkSize, chunkOverlap);
        taskQueuePort.sendSplit(message);
        return TaskSubmitResponseDto.builder()
                .taskId(taskId)
                .message(triggerEmbed ? "场景切分任务已提交（完成后自动向量化）" : "场景切分任务已提交（Split 队列）")
                .build();
    }

    /**
     * 在投递 Split 队列前校验：向量化进行中时不允许再发起场景切分（避免删场景与 Embed 并发冲突）。
     */
    private void assertSceneSplitAllowed(String novelId) {
        Novel n = novelService.getNovelById(novelId);
        if (n != null && n.getStatus() == NovelStatus.EMBEDDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "该小说正在向量化（EMBEDDING），为避免与场景数据冲突，请等待向量化完成后再发起场景切分。");
        }
    }

    private String normalizeNovelId(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.replace(".txt", "");
    }

    private String normalizeVersion(String version) {
        if (version == null || version.trim().isEmpty()) {
            return DEFAULT_VERSION;
        }
        return version.trim();
    }

    private static void ensureChapterTitleRegexValid(String regex) {
        if (regex == null || regex.isBlank()) {
            return;
        }
        try {
            ChapterRecognizer.compileUserPattern(regex);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("chapterTitleRegex 非法: " + e.getMessage());
        }
    }

    /**
     * 提交队列前校验策略字符串；null/空白跳过，未知值抛 {@link IllegalArgumentException}（转为 400）。
     */
    private static void ensureRecognitionStrategyValid(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return;
        }
        RecognitionStrategyType.fromString(strategy);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

}
