package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.port.out.FileStoragePort;
import com.novel.splitter.application.service.novel.ChapterService;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.support.TaskFailureFormatter;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.pipeline.orchestrator.LoadNovelUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadWorker {

    private final LoadNovelUseCase loadNovelUseCase;
    private final TaskService taskService;
    private final NovelCacheRepository novelCacheRepository;
    private final FileStoragePort fileStoragePort;
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

            TaskType taskType = task.getTaskType() != null ? task.getTaskType() : TaskType.CHAPTER_PARSE;
            if (novelId == null || novelId.isBlank()) {
                throw new IllegalArgumentException("novelId must not be blank");
            }

            boolean chapterPhaseOnly = taskType == TaskType.LOAD || taskType == TaskType.CHAPTER_PARSE;

            boolean hasDbChapters = chapterService.hasChapters(novelId);
            Path parsedDir = novelCacheRepository.parsedDirPath(novelId);
            boolean hasParsedFiles = false;
            if (Files.exists(parsedDir)) {
                try (Stream<Path> s = novelCacheRepository.listChapterFiles(novelId)) {
                    hasParsedFiles = s.findAny().isPresent();
                }
            }
            boolean completeArtifacts = hasDbChapters && hasParsedFiles;
            boolean force = message.isForceReload();

            // 幂等短路：完整产物且非强制（仅章节阶段任务在此结束，不再自动投递 Split 队列）
            if (completeArtifacts && !force) {
                if (chapterPhaseOnly) {
                    taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 15,
                            "检测到已完整结构化产物，跳过解析（force=true 可强制重解析）");
                    novelService.updateNovelStatus(novelId, NovelStatus.PARSED);
                    taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "章节解析完成（幂等跳过）");
                    log.info("任务 {} 章节解析幂等跳过", taskId);
                    return;
                }
                log.warn("任务 {} 类型 {} 在 Load 队列上且产物已完整，按章节阶段处理并结束", taskId, taskType);
                novelService.updateNovelStatus(novelId, NovelStatus.PARSED);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "章节产物已就绪（请单独调用场景切分）");
                return;
            }

            if (force && completeArtifacts) {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 8, "强制重解析：清理旧结构化产物...");
                novelCacheRepository.removeParsedArtifacts(novelId);
                chapterService.deleteByNovelId(novelId);
                log.info("任务 {} force=true，已清理 parsed 与 chapters", taskId);
            } else if (!completeArtifacts && (hasDbChapters || hasParsedFiles)) {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 10, "检测到不完整结构化产物，清理后重新解析...");
                novelCacheRepository.removeParsedArtifacts(novelId);
                chapterService.deleteByNovelId(novelId);
                log.warn("任务 {} 不完整结构化（dbChapters={}, parsedFiles={}），已清理",
                        taskId, hasDbChapters, hasParsedFiles);
            }

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 5, "开始读取文件...");
            novelService.updateNovelStatus(novelId, NovelStatus.SPLITTING);

            Path uploadPath = fileStoragePort.toAbsolutePath(task.getFileName());
            Path rawPath = novelCacheRepository.rawOriginalPath(novelId);
            Files.createDirectories(rawPath.getParent());
            if (!Files.exists(rawPath)) {
                Files.copy(uploadPath, rawPath, StandardCopyOption.REPLACE_EXISTING);
            }

            Novel novel = loadNovelUseCase.load(novelId, rawPath, (progress, info) -> {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            }, message.getChapterTitleRegex(), message.getRecognitionStrategy());

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

            novelService.updateNovelStatus(novelId, NovelStatus.PARSED);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100,
                    "章节解析完成：已落库 " + chapterEntities.size() + " 章，请调用场景切分接口继续流水线");
            log.info("任务 {} Load 完成，不再自动投递 Split（解耦章节/场景）", taskId);

        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            String failMsg = TaskFailureFormatter.format("LOAD",
                    TaskFailureFormatter.params("novelId", novelId, "taskId", taskId), e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, failMsg);
            if (novelId != null && !novelId.isBlank()) {
                novelService.updateNovelStatus(novelId, NovelStatus.FAILED);
            }
        }
    }
}
