package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.task.SplitTask;
import com.novel.splitter.application.model.task.SplitTaskMessage;
import com.novel.splitter.application.service.etl.NovelIngestionService;
import com.novel.splitter.application.service.task.ProgressSseService;
import com.novel.splitter.application.service.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmbedWorker {

    private final NovelIngestionService ingestionService;
    private final TaskService taskService;
    private final ProgressSseService progressSseService;

    @RabbitListener(queues = RabbitConfig.EMBED_TASK_QUEUE)
    public void processEmbedTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        log.info("EmbedWorker 接收到向量化任务, taskId: {}", taskId);
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            ingestionService.embedPhase(task.getNovelId(), task.getVersion(), (progress, info) -> {
                progressSseService.send(taskId, progress, info, "RUNNING");
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });
            
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "入库完成");
            progressSseService.send(taskId, 100, "入库完成", "COMPLETED");
            progressSseService.complete(taskId);
            log.info("任务 {} 处理成功", taskId);
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "向量化失败: " + e.getMessage());
            progressSseService.send(taskId, -1, e.getMessage(), "FAILED");
            progressSseService.complete(taskId);
        }
    }
}