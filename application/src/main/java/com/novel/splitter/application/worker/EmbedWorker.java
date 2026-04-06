package com.novel.splitter.application.worker;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.service.novel.NovelService;
import com.novel.splitter.domain.entity.JpaSceneEntity;
import com.novel.splitter.domain.enums.NovelStatus;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.pipeline.orchestrator.EmbedNovelUseCase;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.repository.api.JpaSceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmbedWorker {

    private final EmbedNovelUseCase embedNovelUseCase;
    private final TaskService taskService;
    private final JpaSceneRepository jpaSceneRepository;
    private final NovelService novelService;

    @org.springframework.beans.factory.annotation.Value("${splitter.ingestion.batch-size:100}")
    private int batchSize;

    @RabbitListener(queues = RabbitConfig.EMBED_TASK_QUEUE)
    public void processEmbedTask(EmbedTaskMessage message) {
        String taskId = message.getTaskId();
        String novelId = message.getNovelId();
        
        try {
            SplitTask task = taskService.getTask(taskId);
            if (task == null) {
                log.error("任务 {} 不存在", taskId);
                return;
            }

            if (task.getStatus() == SplitTask.TaskStatus.FAILED || task.getStatus() == SplitTask.TaskStatus.SUCCESS) {
                return;
            }

            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, 0, "开始向量化入库...");
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.EMBEDDING);
            }

            int page = 0;
            int totalScenesProcessed = 0;
            long totalScenes = 0;
            
            while (true) {
                Page<JpaSceneEntity> scenePage = jpaSceneRepository.findByNovelId(novelId, PageRequest.of(page, batchSize));
                if (page == 0) {
                    totalScenes = scenePage.getTotalElements();
                    task.setTotalScenes((int) totalScenes);
                    if (totalScenes == 0) {
                        log.warn("任务 {} 没有找到任何场景数据", taskId);
                        break;
                    }
                }
                
                List<Long> sceneIds = scenePage.getContent().stream()
                        .map(JpaSceneEntity::getId)
                        .collect(Collectors.toList());
                        
                if (sceneIds.isEmpty()) {
                    break;
                }
                
                embedNovelUseCase.embedBatch(sceneIds);
                totalScenesProcessed += sceneIds.size();
                
                int progress = (int) ((totalScenesProcessed / (double) totalScenes) * 100);
                String info = String.format("向量化中：%d/%d", totalScenesProcessed, totalScenes);
                taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.PROCESSING, progress, info);
                
                if (!scenePage.hasNext()) {
                    break;
                }
                page++;
            }
            
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.SUCCESS, 100, "入库完成");
            log.info("任务 {} 处理成功", taskId);
            
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.COMPLETED);
            }
            
        } catch (Exception e) {
            log.error("处理任务 {} 时发生异常", taskId, e);
            taskService.updateTaskStatus(taskId, SplitTask.TaskStatus.FAILED, 0, "向量化失败: " + e.getMessage());
            if (novelId != null) {
                novelService.updateNovelStatus(novelId, NovelStatus.FAILED);
            }
        }
    }
}