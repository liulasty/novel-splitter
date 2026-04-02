package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.task.SplitTask;
import com.novel.splitter.application.model.task.SplitTaskMessage;
import com.novel.splitter.application.service.etl.NovelIngestionService;
import com.novel.splitter.application.service.task.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@RequiredArgsConstructor
@Slf4j
public class SplitWorker {

    private final NovelIngestionService ingestionService;
    private final TaskService taskService;

    @RabbitListener(queues = RabbitConfig.SPLIT_TASK_QUEUE)
    public void processSplitTask(SplitTaskMessage message) {
        log.info("Worker 接收到切分任务, taskId: {}, novelId: {}", message.getTaskId(), message.getNovelId());
        
        try {
            taskService.updateTaskStatus(message.getTaskId(), SplitTask.TaskStatus.PROCESSING, 10, "开始解析和切分...");
            
            Path novelPath = Paths.get(message.getFilePath());
            
            ingestionService.ingest(novelPath, message.getMaxScenes(), message.getVersion(), (progress, info) -> {
                taskService.updateTaskStatus(message.getTaskId(), SplitTask.TaskStatus.PROCESSING, progress, info);
            });
            
            taskService.updateTaskStatus(message.getTaskId(), SplitTask.TaskStatus.SUCCESS, 100, "入库完成");
            log.info("任务 {} 处理成功", message.getTaskId());
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", message.getTaskId(), e);
            taskService.updateTaskStatus(message.getTaskId(), SplitTask.TaskStatus.FAILED, 0, "入库失败: " + e.getMessage());
        }
    }
}
