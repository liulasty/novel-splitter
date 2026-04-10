package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.SplitTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitTaskQueueAdapter implements TaskQueuePort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void sendToLoadQueue(SplitTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
        log.info("Sent taskId {} to load queue", message.getTaskId());
    }

    @Override
    public void sendToSplitQueue(SplitTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "split", message);
        log.info("Sent taskId {} to split queue", message.getTaskId());
    }

    @Override
    public void sendToEmbedQueue(EmbedTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "embed", message);
        log.info("Sent taskId {} to embed queue", message.getTaskId());
    }
}