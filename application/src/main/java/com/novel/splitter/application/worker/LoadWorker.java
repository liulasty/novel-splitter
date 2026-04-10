package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.pipeline.orchestrator.LoadNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.novel.ChapterService;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.Novel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadWorker {

    private final LoadNovelUseCase loadNovelUseCase;
    private final TaskService taskService;
    private final NovelCacheRepository novelCacheRepository;
    private final RabbitTemplate rabbitTemplate;
    private final AppConfig appConfig;
    private final NovelService novelService;
    private final ChapterService chapterService;

    @RabbitListener(queues = RabbitConfig.LOAD_TASK_QUEUE)
    public void processLoadTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        String novelId = message.getNovelId();
        log.info("LoadWorker 接收到加载任务, taskId: {}", taskId);
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 5, "开始读取文件...");
            if (novelId == null || novelId.isBlank()) {
                throw new IllegalArgumentException("novelId must not be blank");
            }
            novelService.updateNovelStatus(novelId, NovelStatus.SPLITTING);

            // Idempotency (strict): skip only when BOTH parsed files exist AND DB chapters exist.
            boolean hasDbChapters = chapterService.hasChapters(novelId);
            Path parsedDir = novelCacheRepository.parsedDirPath(novelId);
            boolean hasParsedFiles = false;
            if (Files.exists(parsedDir)) {
                try (Stream<Path> s = novelCacheRepository.listChapterFiles(novelId)) {
                    hasParsedFiles = s.findAny().isPresent();
                }
            }
            if (hasDbChapters && hasParsedFiles) {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 15, "检测到已完整结构化产物，跳过解析，进入切分阶段");
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "split", message);
                log.info("任务 {} 已完整结构化（dbChapters={}, parsedFiles={}），已直接发送至 Split 队列", taskId, hasDbChapters, hasParsedFiles);
                return;
            }
            if (hasDbChapters || hasParsedFiles) {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 10, "检测到不完整结构化产物，清理后重新解析...");
                // Prevent partial data from flowing downstream.
                novelCacheRepository.removeParsedArtifacts(novelId);
                chapterService.deleteByNovelId(novelId);
                log.warn("任务 {} 检测到不完整结构化状态（dbChapters={}, parsedFiles={}），已清理 parsedDir 与 chapters 记录，准备重新解析",
                        taskId, hasDbChapters, hasParsedFiles);
            }

            String rootPath = appConfig.getStorage().getRootPath();
            Path uploadPath = Paths.get(rootPath, task.getFileName());
            Path rawPath = novelCacheRepository.rawOriginalPath(novelId);
            Files.createDirectories(rawPath.getParent());
            if (!Files.exists(rawPath)) {
                Files.copy(uploadPath, rawPath, StandardCopyOption.REPLACE_EXISTING);
            }
            
            Novel novel = loadNovelUseCase.load(novelId, rawPath, (progress, info) -> {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });

            Novel novelEntity = novelService.getNovelById(novelId);
            List<Chapter> chapterEntities = novel.getChapters().stream()
                    .map(chapter -> Chapter.builder()
                            .novelId(novelEntity.getId())
                            .title(chapter.getTitle())
                            .index(chapter.getIndex())
                            .startParagraphIndex(chapter.getStartParagraphIndex())
                            .endParagraphIndex(chapter.getEndParagraphIndex())
                            .wordCount(chapter.getWordCount())
                            .build())
                    .collect(Collectors.toList());
            chapterService.saveChapters(chapterEntities);
            log.info("任务 {} 成功将 {} 个章节保存至数据库", taskId, chapterEntities.size());

            // Send to split queue
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "split", message);
            log.info("任务 {} Load 阶段完成，已发送至 Split 队列", taskId);
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "读取失败: " + e.getMessage());
            if (novelId != null && !novelId.isBlank()) {
                novelService.updateNovelStatus(novelId, NovelStatus.FAILED);
            }
        }
    }
}