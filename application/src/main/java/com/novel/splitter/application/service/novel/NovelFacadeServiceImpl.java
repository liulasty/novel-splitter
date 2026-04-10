package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.NovelPipelineRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.model.dto.NovelSummaryDto;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.domain.model.paging.PageQuery;
import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.SplitRetryRequestDto;
import com.novel.splitter.domain.task.SplitTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NovelFacadeServiceImpl implements NovelFacadeService {

    private static final String DEFAULT_VERSION = "v1";

    private final NovelStorageService novelStorageService;
    private final NovelService novelService;
    private final ChapterService chapterService;
    private final com.novel.splitter.domain.repository.NovelCacheRepository novelCacheRepository;
    private final TaskService taskService;
    private final RabbitTemplate rabbitTemplate;
    private final DownloadService downloadService;
    private final AppConfig appConfig;
    private final com.novel.splitter.domain.repository.SceneRepository sceneRepository;
    private final com.novel.splitter.domain.repository.ChapterRepository chapterRepository;
    private final DtoMapper dtoMapper;

    @Override
    public List<NovelStatRecordDto> getNovelStats() {
        List<Object[]> sceneCounts = sceneRepository.countScenesByNovelAndVersion();

        // Map: novelId -> map of version -> count
        Map<String, Map<String, Long>> novelVersionCounts = new HashMap<>();
        for (Object[] row : sceneCounts) {
            String novelId = (String) row[0];
            String version = (String) row[1];
            long count = ((Number) row[2]).longValue();
            novelVersionCounts.computeIfAbsent(novelId, k -> new HashMap<>()).put(version, count);
        }

        List<SplitTask> allTasks = taskService.getAllTasks();
        // Group tasks by novelId
        Map<String, List<SplitTask>> tasksByNovel = allTasks.stream()
                .filter(t -> t.getNovelId() != null)
                .collect(Collectors.groupingBy(SplitTask::getNovelId));

        List<NovelStatRecordDto> stats = new ArrayList<>();

        // Merge data
        for (Map.Entry<String, Map<String, Long>> entry : novelVersionCounts.entrySet()) {
            String novelId = entry.getKey();
            Map<String, Long> versionMap = entry.getValue();

            List<String> versions = new ArrayList<>(versionMap.keySet());
            long totalScenes = versionMap.values().stream().mapToLong(Long::longValue).sum();

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

            String titleForDisplay = novelService.getNovelById(novelId) != null ? novelService.getNovelById(novelId).getTitle() : novelId;

            NovelStatRecordDto dto = NovelStatRecordDto.builder()
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
            if (!novelVersionCounts.containsKey(novelId)) {
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

                String titleForDisplay = novelService.getNovelById(novelId) != null ? novelService.getNovelById(novelId).getTitle() : novelId;
                NovelStatRecordDto dto = NovelStatRecordDto.builder()
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
    public List<NovelSummaryDto> listNovelsFromDb() {
        return novelService.listNovels().stream()
                .filter(n -> n != null && !n.isDeleted())
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
    public NovelUploadResponseDto uploadNovel(MultipartFile file, String title, String author, String description) throws IOException {
        String novelId = novelService.createNovel(file, title, author, description);
        return NovelUploadResponseDto.builder()
                .message("文件上传成功")
                .novelId(novelId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto split(String novelId, IngestRequest request) throws IOException {
        return split(novelId, request, false);
    }

    @Override
    public TaskSubmitResponseDto retrySplit(String novelId, SplitRetryRequestDto request) throws IOException {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(id);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + id);
        }

        boolean hasDbChapters = chapterService.hasChapters(id);
        boolean hasParsedFiles = false;
        try (java.util.stream.Stream<java.nio.file.Path> s = novelCacheRepository.listChapterFiles(id)) {
            hasParsedFiles = s.findAny().isPresent();
        } catch (Exception e) {
            // treat as not ready
            hasParsedFiles = false;
        }
        if (!hasDbChapters || !hasParsedFiles) {
            throw new IllegalStateException("Split retry requires structured artifacts. hasDbChapters=" + hasDbChapters + ", hasParsedFiles=" + hasParsedFiles);
        }

        String taskId = UUID.randomUUID().toString();
        int maxScenes = (request != null && request.getMaxScenes() > 0) ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request != null ? request.getVersion() : null);
        boolean triggerEmbed = request != null && request.isTriggerEmbed();

        taskService.createTask(taskId, TaskType.SPLIT, id, novel.getFilePath(), maxScenes, version);
        SplitTaskMessage message = new SplitTaskMessage(taskId, id, maxScenes, version, triggerEmbed);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "split", message);
        log.info("Sent taskId {} to split queue for retrySplit", taskId);

        return TaskSubmitResponseDto.builder()
                .message("切分重试任务已提交到队列（跳过 Load）")
                .taskId(taskId)
                .build();
    }

    private TaskSubmitResponseDto split(String novelId, IngestRequest request, boolean triggerEmbed) throws IOException {
        log.info("接收到切分请求: novelId={}, request={}", novelId, request);
        
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }

        String taskId = UUID.randomUUID().toString();
        int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request.getVersion());

        taskService.createTask(taskId, TaskType.SPLIT, novelId, novel.getFilePath(), maxScenes, version);
        
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version, triggerEmbed);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue for split", taskId);

        return TaskSubmitResponseDto.builder()
                .message("切分任务已提交到队列")
                .taskId(taskId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto embed(String novelId) throws IOException {
        log.info("接收到向量化请求: novelId={}", novelId);
        
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }

        String taskId = UUID.randomUUID().toString();
        // Here we assume the latest version or a default one if version is not provided in request
        String version = DEFAULT_VERSION;
        
        taskService.createTask(taskId, TaskType.EMBED, novelId, novel.getFilePath(), Integer.MAX_VALUE, version);
        
        EmbedTaskMessage message = new EmbedTaskMessage(taskId, novelId, version);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "embed", message);
        log.info("Sent taskId {} to embed queue", taskId);

        return TaskSubmitResponseDto.builder()
                .message("向量化任务已提交到队列")
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

        if (hasSplit) {
            IngestRequest ingestRequest = new IngestRequest();
            ingestRequest.setVersion(request.getVersion());
            ingestRequest.setMaxScenes(request.getMaxScenes());
            TaskSubmitResponseDto splitTask = split(novelId, ingestRequest, hasEmbed);
            if (hasEmbed) {
                splitTask.setMessage("全流程任务已提交：SPLIT -> EMBED");
            }
            return splitTask;
        }

        return embed(novelId);
    }

    @Override
    public TaskSubmitResponseDto ingest(IngestRequest request) throws IOException {
        // 保留原有的 ingest 作为向前兼容，或者直接复用
        log.info("接收到原 ingest 请求: {}", request);

        novelStorageService.resolveExistingNovelPath(request.getFileName());
        String taskId = UUID.randomUUID().toString();
        String novelId = normalizeNovelId(request.getFileName());
        int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request.getVersion());

        taskService.createTask(taskId, TaskType.SPLIT, novelId, request.getFileName(), maxScenes, version);
        
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue", taskId);

        log.info("入库任务已发送到队列, taskId: {}", taskId);
        return TaskSubmitResponseDto.builder()
                .message("入库任务已提交到队列")
                .taskId(taskId)
                .build();
    }

    @Override
    public TaskSubmitResponseDto downloadAndIngest(DownloadAndIngestRequest request) throws IOException {
        log.info("接收到下载并入库请求: url={}, name={}", request.getUrl(), request.getName());
        
        // 1. 同步下载，得到落盘文件名
        String savedFileName = downloadService.downloadNovel(request.getUrl(), request.getName());
        String relativePath = toStorageRelativePath(savedFileName);

        // 2. 下载后先创建 Novel 资源，再统一走 novelId 驱动 pipeline
        String novelId = novelService.createNovelFromStoredFile(relativePath, request.getName(), null, "downloaded from " + request.getUrl());

        NovelPipelineRequestDto pipelineRequest = new NovelPipelineRequestDto();
        pipelineRequest.setVersion(request.getVersion());
        pipelineRequest.setMaxScenes(request.getMaxScenes());
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
    public Page<com.novel.splitter.application.model.dto.SceneDto> getScenesByChapter(String novelId, Long chapterId, int page, int size) {
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }
        novel.checkCanReadChapters();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 500);
        PagedResult<com.novel.splitter.application.model.dto.SceneDto> result = sceneRepository
                .findByNovelIdAndChapterId(novelId, chapterId, PageQuery.of(safePage, safeSize))
                .map(dtoMapper::toSceneDto);
        return new PageImpl<>(result.getContent(), org.springframework.data.domain.PageRequest.of(safePage, safeSize), result.getTotalElements());
    }

    @Override
    public void softDeleteNovel(String novelId) {
        if (novelId == null || novelId.isBlank()) {
            throw new IllegalArgumentException("novelId must not be blank");
        }
        String id = novelId.trim();
        // Soft delete novel row first; also soft delete chapters/scenes for visibility.
        novelService.softDeleteNovel(id);
        chapterRepository.deleteByNovelId(id);
        sceneRepository.deleteNovelById(id);
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

    private String toStorageRelativePath(String absoluteOrRelativePath) {
        if (absoluteOrRelativePath == null || absoluteOrRelativePath.isBlank()) {
            throw new IllegalArgumentException("downloaded file path is empty");
        }
        Path storageRoot = Paths.get(appConfig.getStorage().getRootPath()).toAbsolutePath().normalize();
        Path downloaded = Paths.get(absoluteOrRelativePath).toAbsolutePath().normalize();
        if (!downloaded.startsWith(storageRoot)) {
            throw new IllegalArgumentException("downloaded file is outside storage root: " + downloaded);
        }
        return storageRoot.relativize(downloaded).toString().replace('\\', '/');
    }
}
