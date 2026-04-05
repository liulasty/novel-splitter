package com.novel.splitter.application.service.task;

import com.novel.splitter.domain.task.TaskProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseBridgeConsumer {

    private final TaskSseService taskSseService;

    @RabbitListener(queues = "#{notifyTaskQueue.name}")
    public void onTaskProgressEvent(TaskProgressEvent event) {
        log.debug("Received broadcast progress event for task {}: {}", event.getTaskId(), event.getMessage());
        taskSseService.broadcast(event);
    }
}
