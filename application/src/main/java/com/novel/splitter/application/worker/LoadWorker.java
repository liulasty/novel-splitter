package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.pipeline.etl.NovelCacheService;
import com.novel.splitter.pipeline.orchestrator.LoadNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.Novel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoadWorker {

    private final LoadNovelUseCase loadNovelUseCase;
    private final TaskService taskService;
    private final NovelCacheService novelCacheService;
    private final RabbitTemplate rabbitTemplate;
    private final AppConfig appConfig;

    @RabbitListener(queues = RabbitConfig.LOAD_TASK_QUEUE)
    public void processLoadTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        log.info("LoadWorker 接收到加载任务, taskId: {}", taskId);
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 5, "开始读取文件...");

            String rootPath = appConfig.getStorage().getRootPath();
            Path novelPath = Paths.get(rootPath, task.getFileName());
            
            Novel novel = loadNovelUseCase.load(taskId, novelPath, (progress, info) -> {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });

            novelCacheService.save(taskId, novel);

            // Send to split queue
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "split", message);
            log.info("任务 {} Load 阶段完成，已发送至 Split 队列", taskId);
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "读取失败: " + e.getMessage());
        }
    }
}