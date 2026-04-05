package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.application.service.etl.NovelIngestionService;
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

    @RabbitListener(queues = RabbitConfig.EMBED_TASK_QUEUE)
    public void processEmbedTask(EmbedTaskMessage message) {
        String taskId = message.getTaskId();
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            // 如果任务已经失败或成功，不继续处理后续批次
            if (task.getStatus() == SplitTask.TaskStatus.FAILED || task.getStatus() == SplitTask.TaskStatus.SUCCESS) {
                return;
            }

            ingestionService.embedPhaseBatch(message.getSceneIds());

            // 更新进度
            int completed = task.getCompletedScenes().addAndGet(message.getSceneIds().size());
            int total = task.getTotalScenes();
            
            // 假设 65% 到 100% 是 Embed 阶段
            int progress = 65 + (int) ((completed / (double) total) * 35);
            String info = String.format("向量化中：%d/%d", completed, total);
            
            // 为了避免过多更新导致数据库压力和前端频繁刷新，可以加上一定限制
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            
            if (completed >= total) {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "入库完成");
                log.info("任务 {} 处理成功", taskId);
            }
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "向量化失败: " + e.getMessage());
        }
    }
}