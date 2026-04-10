package com.novel.splitter.interfaces.infra;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.port.out.TaskQueuePort;
import com.novel.splitter.domain.task.EmbedTaskMessage;
import com.novel.splitter.domain.task.EnrichTaskMessage;
import com.novel.splitter.domain.task.SplitTaskMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RabbitTaskQueueAdapter implements TaskQueuePort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void sendLoad(SplitTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "load", message);
    }

    @Override
    public void sendSplit(SplitTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "split", message);
    }

    @Override
    public void sendEmbed(EmbedTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "embed", message);
    }

    @Override
    public void sendEnrich(EnrichTaskMessage message) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "enrich", message);
    }
}

