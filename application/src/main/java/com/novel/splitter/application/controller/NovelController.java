package com.novel.splitter.application.controller;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.task.SplitTask;
import com.novel.splitter.application.model.task.SplitTaskMessage;
import com.novel.splitter.application.service.task.ProgressSseService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.dto.IngestRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 小说文件管理控制器
 * 提供小说的列表查询、上传及入库（向量化）处理功能
 */
@Tag(name = "小说文件管理", description = "提供小说的列表查询、上传及入库（向量化）处理功能")
@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
@Slf4j
public class NovelController {

    private final TaskService taskService;
    private final RabbitTemplate rabbitTemplate;
    private final AppConfig appConfig;
    private final ProgressSseService progressSseService;
    private final com.novel.splitter.application.service.etl.NovelIngestionService novelIngestionService;

    /**
     * 接收小说入库进度流
     */
    @Operation(summary = "获取入库进度SSE流", description = "返回SSE流以实时推送进度")
    @GetMapping(value = "/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@RequestParam("taskId") String taskId) {
        return progressSseService.connect(taskId);
    }

    /**
     * 获取存储路径
     *
     * @return 存储目录的 Path 对象
     * @throws IOException 目录创建失败时抛出异常
     */
    private Path getStoragePath() throws IOException {
        Path path = Paths.get(appConfig.getStorage().getRootPath());
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }
        return path;
    }

    /**
     * 列出所有可用的本地小说文件
     *
     * @return 小说文件名列表
     */
    @Operation(summary = "获取小说列表", description = "列出服务器本地存储目录下的所有 .txt 格式小说文件")
    @GetMapping
    public ResponseEntity<List<String>> listNovels() {
        try {
            Path storagePath = getStoragePath();
            try (Stream<Path> stream = Files.list(storagePath)) {
                List<String> files = stream
                        .filter(file -> !Files.isDirectory(file))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .filter(name -> name.endsWith(".txt"))
                        .collect(Collectors.toList());
                return ResponseEntity.ok(files);
            }
        } catch (Exception e) {
            log.error("获取小说列表失败", e);
            return ResponseEntity.internalServerError().body(Collections.emptyList());
        }
    }

    /**
     * 上传小说文件
     *
     * @param file 待上传的文件
     * @return 包含上传结果和新文件名的响应实体
     */
    @Operation(summary = "上传小说文件", description = "上传本地小说文件到服务器存储目录，并自动生成唯一文件名")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, String>> uploadNovel(
            @Parameter(description = "上传的文件对象", required = true) @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        try {
            String originalFilename = file.getOriginalFilename();
            String newFilename = generateUniqueFilename(originalFilename);
            Path destination = getStoragePath().resolve(newFilename);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
            return ResponseEntity.ok(Map.of("message", "文件上传成功: " + newFilename, "fileName", newFilename));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /**
     * 生成唯一文件名（附加时间戳）
     *
     * @param originalFilename 原始文件名
     * @return 唯一文件名
     */
    private String generateUniqueFilename(String originalFilename) {
        if (originalFilename == null) return "unknown_" + System.currentTimeMillis() + ".txt";
        
        String name = originalFilename;
        String ext = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0) {
            name = originalFilename.substring(0, dotIndex);
            ext = originalFilename.substring(dotIndex);
        }
        
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = java.time.LocalDateTime.now().format(formatter);
        
        return name + "_" + timestamp + ext;
    }

    /**
     * 启动小说入库（解析、分块、向量化）处理
     *
     * @param request 入库请求参数
     * @return 启动入库任务的响应信息
     */
    @Operation(summary = "小说入库处理", description = "异步启动指定小说文件的解析、分块及向量化入库流程")
    @PostMapping("/ingest")
    public ResponseEntity<Map<String, String>> ingest(@RequestBody IngestRequest request) {
        log.info("接收到入库请求: {}", request);
        try {
            Path novelPath = getStoragePath().resolve(request.getFileName());
            if (!Files.exists(novelPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "找不到文件: " + request.getFileName()));
            }
            
            String taskId = UUID.randomUUID().toString();
            String novelId = request.getFileName().replace(".txt", "");
            
            int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
            String version = request.getVersion() != null && !request.getVersion().trim().isEmpty() ? request.getVersion() : "v1";

            // 1. Create task in DB
            taskService.createTask(taskId, novelId, request.getFileName(), maxScenes, version);
            
            // 2. Send message to RabbitMQ via IngestionService
            novelIngestionService.ingestAsync(taskId, novelId, novelPath.toAbsolutePath().toString(), maxScenes, version);
            
            log.info("切分任务已发送到队列, taskId: {}", taskId);

            return ResponseEntity.ok(Map.of(
                "message", "入库任务已提交到队列",
                "taskId", taskId
            ));
        } catch (Exception e) {
            log.error("启动入库任务失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
