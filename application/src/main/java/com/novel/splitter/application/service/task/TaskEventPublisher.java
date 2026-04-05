package com.novel.splitter.application.service.task;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.repository.task.TaskEventRepository;
import com.novel.splitter.domain.task.TaskProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final TaskEventRepository taskEventRepository;

    public void publish(String taskId, int progress, String message, String status) {
        TaskProgressEvent event = new TaskProgressEvent(taskId, progress, message, status);
        
        // 1. 追加审计日志到 task_events 表（时光倒流能力）
        try {
            taskEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to save TaskProgressEvent for taskId: {}", taskId, e);
        }

        // 2. 发送给 MQ 广播（副作用隔离）
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.NOTIFY_EXCHANGE_NAME, "", event);
        } catch (Exception e) {
            log.warn("Failed to publish TaskProgressEvent for taskId: {}", taskId, e);
        }
    }
}

