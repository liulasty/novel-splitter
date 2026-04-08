package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskMessage;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.domain.enums.TaskType;
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

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SplitWorker {

    private final SplitNovelUseCase splitNovelUseCase;
    private final TaskService taskService;
    private final NovelCacheRepository novelCacheRepository;
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
                task = taskService.createTask(
                        taskId,
                        TaskType.SPLIT,
                        message.getNovelId(),
                        message.getNovelId(),
                        message.getMaxScenes(),
                        message.getVersion()
                );
            }

            Novel novel = novelCacheRepository.load(taskId);
            
            List<Long> sceneIds = splitNovelUseCase.split(taskId, message.getNovelId(), novel, task.getMaxScenes(), task.getVersion(), (progress, info) -> {
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
            });
            // 清理缓存
            novelCacheRepository.remove(taskId);

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
                // [FUTURE] 预留: 发送消息到 ENRICH_TASK_QUEUE 进行 AI 语义增强
                // rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "enrich", new EnrichTaskMessage(taskId, message.getNovelId(), ...));
                log.info("=== [AI Enrichment Placeholder] === Would trigger AI enrichment MQ task here for novel {}", message.getNovelId());
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
