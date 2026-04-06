package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.pipeline.etl.NovelCacheService;
import com.novel.splitter.pipeline.orchestrator.SplitNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.enums.NovelStatus;
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

    private final SplitNovelUseCase splitNovelUseCase;
    private final TaskService taskService;
    private final NovelCacheService novelCacheService;
    private final RabbitTemplate rabbitTemplate;

    private final NovelService novelService;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.batch-size:10}")
    private int batchSize;

    @RabbitListener(queues = RabbitConfig.SPLIT_TASK_QUEUE)
    public void processSplitTask(SplitTaskMessage message) {
        String taskId = message.getTaskId();
        log.info("SplitWorker 接收到切分任务, taskId: {}", taskId);
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.warn("任务 {} 在内存中不存在，可能由于服务重启，正在尝试自动重建...", taskId);
                task = taskService.createTask(taskId, message.getNovelId(), message.getFilePath(), message.getMaxScenes(), message.getVersion());
            }

            Novel novel = novelCacheService.load(taskId);
            
            List<Long> sceneIds = splitNovelUseCase.split(taskId, message.getNovelId(), novel, task.getMaxScenes(), task.getVersion(), (progress, info) -> {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });
            // 清理缓存
            novelCacheService.remove(taskId);

            if (sceneIds == null || sceneIds.isEmpty()) {
                log.warn("任务 {} 切分后没有场景，直接标记为成功", taskId);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分完成，无有效场景");
                if (message.getNovelId() != null) {
                    novelService.updateNovelStatus(message.getNovelId(), NovelStatus.SPLIT_COMPLETED);
                }
                return;
            }

            // 更新总场景数
            task.setTotalScenes(sceneIds.size());
            task.getCompletedScenes().set(sceneIds.size());
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "切分阶段完成，数据已落盘");
            
            if (message.getNovelId() != null) {
                novelService.updateNovelStatus(message.getNovelId(), NovelStatus.SPLIT_COMPLETED);
            }
            
            log.info("任务 {} Split 阶段完成，共处理 {} 个场景", taskId, sceneIds.size());
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "切分失败: " + e.getMessage());
            if (message.getNovelId() != null) {
                novelService.updateNovelStatus(message.getNovelId(), NovelStatus.FAILED);
            }
        }
    }
}
