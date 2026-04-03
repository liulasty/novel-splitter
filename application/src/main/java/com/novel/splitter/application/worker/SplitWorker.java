package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.task.SplitTask;
import com.novel.splitter.application.model.task.SplitTaskMessage;
import com.novel.splitter.application.model.task.EmbedTaskMessage;
import com.novel.splitter.application.service.etl.NovelCacheService;
import com.novel.splitter.application.service.etl.NovelIngestionService;
import com.novel.splitter.application.service.task.ProgressSseService;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.model.Novel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SplitWorker {

    private final NovelIngestionService ingestionService;
    private final TaskService taskService;
    private final ProgressSseService progressSseService;
    private final NovelCacheService novelCacheService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.SPLIT_TASK_QUEUE)
    public void processSplitTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        log.info("SplitWorker 接收到切分任务, taskId: {}", taskId);
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            Novel novel = novelCacheService.load(taskId);
            
            List<Long> sceneIds = ingestionService.splitPhase(taskId, novel, task.getMaxScenes(), task.getVersion(), (progress, info) -> {
                progressSseService.send(taskId, progress, info, "RUNNING");
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });
            
            novelCacheService.remove(taskId); // 清理缓存

            if (sceneIds == null || sceneIds.isEmpty()) {
                log.warn("任务 {} 切分后没有场景，直接标记为成功", taskId);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分完成，无有效场景");
                progressSseService.send(taskId, 100, "切分完成，无有效场景", "COMPLETED");
                progressSseService.complete(taskId);
                return;
            }

            // 更新总场景数
            task.setTotalScenes(sceneIds.size());
            task.getCompletedScenes().set(0);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 65, "正在分发向量化任务...");

            // Send batches to embed queue
            int batchSize = 10;
            for (int i = 0; i < sceneIds.size(); i += batchSize) {
                int end = Math.min(i + batchSize, sceneIds.size());
                List<Long> batch = new ArrayList<>(sceneIds.subList(i, end));
                EmbedTaskMessage embedMessage = new EmbedTaskMessage(taskId, message.getNovelId(), message.getVersion(), batch);
                rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "embed", embedMessage);
            }
            
            log.info("任务 {} Split 阶段完成，已将 {} 个场景分批发送至 Embed 队列", taskId, sceneIds.size());
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "切分失败: " + e.getMessage());
            progressSseService.send(taskId, -1, e.getMessage(), "FAILED");
            progressSseService.complete(taskId);
        }
    }
}
