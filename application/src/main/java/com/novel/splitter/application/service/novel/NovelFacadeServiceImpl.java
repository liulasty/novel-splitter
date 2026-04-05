package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.domain.model.dto.IngestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class NovelFacadeServiceImpl implements NovelFacadeService {

    private static final String DEFAULT_VERSION = "v1";

    private final NovelStorageService novelStorageService;
    private final TaskService taskService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public List<String> listNovels() throws IOException {
        return novelStorageService.listNovels();
    }

    @Override
    public Map<String, String> uploadNovel(MultipartFile file) throws IOException {
        String newFilename = novelStorageService.saveNovel(file);
        return Map.of("message", "文件上传成功: " + newFilename, "fileName", newFilename);
    }

    @Override
    public Map<String, String> ingest(IngestRequest request) throws IOException {
        log.info("接收到入库请求: {}", request);

        Path novelPath = novelStorageService.resolveExistingNovelPath(request.getFileName());
        String taskId = UUID.randomUUID().toString();
        String novelId = normalizeNovelId(request.getFileName());
        int maxScenes = request.getMaxScenes() > 0 ? request.getMaxScenes() : Integer.MAX_VALUE;
        String version = normalizeVersion(request.getVersion());

        taskService.createTask(taskId, novelId, request.getFileName(), maxScenes, version);
        
        SplitTaskMessage message = new SplitTaskMessage(taskId, novelId, novelPath.toAbsolutePath().toString(), maxScenes, version);
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue", taskId);

        log.info("入库任务已发送到队列, taskId: {}", taskId);
        return Map.of("message", "入库任务已提交到队列", "taskId", taskId);
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
