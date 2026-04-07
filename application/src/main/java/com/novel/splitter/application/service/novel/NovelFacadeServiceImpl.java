package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.domain.task.SplitTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NovelFacadeServiceImpl implements NovelFacadeService {

    private static final String DEFAULT_VERSION = "v1";

    private final NovelStorageService novelStorageService;
    private final NovelService novelService;
    private final ChapterService chapterService;
    private final TaskService taskService;
    private final RabbitTemplate rabbitTemplate;
    private final DownloadService downloadService;
    private final com.novel.splitter.domain.repository.SceneRepository sceneRepository;

    @Override
    public List<NovelStatRecordDto> getNovelStats() {
        List<Object[]> sceneCounts = sceneRepository.countScenesByNovelAndVersion();

        // Map: novelName -> map of version -> count
        Map<String, Map<String, Long>> novelVersionCounts = new HashMap<>();
        for (Object[] row : sceneCounts) {
            String novelName = (String) row[0];
            String version = (String) row[1];
            long count = ((Number) row[2]).longValue();
            novelVersionCounts.computeIfAbsent(novelName, k -> new HashMap<>()).put(version, count);
        }

        List<SplitTask> allTasks = taskService.getAllTasks();
        // Group tasks by novelId
        Map<String, List<SplitTask>> tasksByNovel = allTasks.stream()
                .filter(t -> t.getNovelId() != null)
                .collect(Collectors.groupingBy(SplitTask::getNovelId));

        List<NovelStatRecordDto> stats = new ArrayList<>();

        // Merge data
        for (Map.Entry<String, Map<String, Long>> entry : novelVersionCounts.entrySet()) {
            String novelName = entry.getKey();
            Map<String, Long> versionMap = entry.getValue();

            List<String> versions = new ArrayList<>(versionMap.keySet());
            long totalScenes = versionMap.values().stream().mapToLong(Long::longValue).sum();

            // Get latest task for this novel
            List<SplitTask> novelTasks = tasksByNovel.getOrDefault(novelName, Collections.emptyList());
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

            NovelStatRecordDto dto = NovelStatRecordDto.builder()
                    .novelName(novelName)
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
            String novelName = entry.getKey();
            if (!novelVersionCounts.containsKey(novelName)) {
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

                NovelStatRecordDto dto = NovelStatRecordDto.builder()
                        .novelName(novelName)
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
    public Map<String, String> uploadNovel(MultipartFile file, String title, String author, String description) throws IOException {
        String novelId = novelService.createNovel(file, title, author, description);
        return Map.of("message", "文件上传成功", "novelId", novelId);
    }

    @Override
    public Map<String, String> split(String novelId, IngestRequest request) throws IOException {
        log.info("接收到切分请求: novelId={}, request={}", novelId, request);
        
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }

        String taskId = UUID.randomUUID().toString();
        int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request.getVersion());

        taskService.createTask(taskId, TaskType.SPLIT, novelId, novel.getFilePath(), maxScenes, version);
        
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue for split", taskId);

        return Map.of("message", "切分任务已提交到队列", "taskId", taskId);
    }

    @Override
    public Map<String, String> embed(String novelId) throws IOException {
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

        return Map.of("message", "向量化任务已提交到队列", "taskId", taskId);
    }

    @Override
    public Map<String, String> ingest(IngestRequest request) throws IOException {
        // 保留原有的 ingest 作为向前兼容，或者直接复用
        log.info("接收到原 ingest 请求: {}", request);

        Path novelPath = novelStorageService.resolveExistingNovelPath(request.getFileName());
        String taskId = UUID.randomUUID().toString();
        String novelId = normalizeNovelId(request.getFileName());
        int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request.getVersion());

        taskService.createTask(taskId, TaskType.SPLIT, novelId, request.getFileName(), maxScenes, version);
        
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, maxScenes, version);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue", taskId);

        log.info("入库任务已发送到队列, taskId: {}", taskId);
        return Map.of("message", "入库任务已提交到队列", "taskId", taskId);
    }

    @Override
    public Map<String, String> downloadAndIngest(DownloadAndIngestRequest request) throws IOException {
        log.info("接收到下载并入库请求: url={}, name={}", request.getUrl(), request.getName());
        
        // 1. 同步下载，得到落盘文件名
        String savedFileName = downloadService.downloadNovel(request.getUrl(), request.getName());
        String fileName = Paths.get(savedFileName).getFileName().toString();
        
        // 2. 复用已有 ingest 入口，异步进入 Worker 链路
        IngestRequest ingestRequest = new IngestRequest();
        ingestRequest.setFileName(fileName);
        ingestRequest.setVersion(request.getVersion());
        ingestRequest.setMaxScenes(request.getMaxScenes());
        
        return ingest(ingestRequest);
    }

    @Override
    public List<com.novel.splitter.application.model.dto.ChapterDto> getChapters(String novelId) {
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }
        if (novel.getStatus() == NovelStatus.PENDING || 
            novel.getStatus() == NovelStatus.SPLITTING) {
            throw new IllegalStateException("小说正在切分中，请稍后再试");
        }
        return com.novel.splitter.application.mapper.DtoMapper.INSTANCE.toChapterDtos(chapterService.getChaptersByNovelId(novelId));
    }

    @Override
    public List<com.novel.splitter.application.model.dto.SceneDto> getScenesByChapter(String novelId, Long chapterId) {
        com.novel.splitter.domain.model.Novel novel = novelService.getNovelById(novelId);
        if (novel == null) {
            throw new IllegalArgumentException("Novel not found: " + novelId);
        }
        if (novel.getStatus() == NovelStatus.PENDING || 
            novel.getStatus() == NovelStatus.SPLITTING) {
            throw new IllegalStateException("小说正在切分中，请稍后再试");
        }
        return sceneRepository.findByNovelIdAndChapterId(novelId, chapterId).stream()
          .map(com.novel.splitter.application.mapper.DtoMapper.INSTANCE::toSceneDto)
          .collect(Collectors.toList());
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
}
