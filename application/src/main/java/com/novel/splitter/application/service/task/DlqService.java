package com.novel.splitter.application.service.task;

import com.novel.splitter.application.config.RabbitConfig;
import com.novel.splitter.application.model.dto.DlqRequeueResultDto;
import com.novel.splitter.application.model.dto.DlqStatDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
/**
 * 死信队列（DLQ）积压查询与消息重投到业务队列。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DlqService {

    private static final Map<String, String> DLQ_TO_ROUTING = Map.of(
            RabbitConfig.LOAD_TASK_DLQ, "load",
            RabbitConfig.SPLIT_TASK_DLQ, "split",
            RabbitConfig.EMBED_TASK_DLQ, "embed",
            RabbitConfig.CLEANUP_TASK_DLQ, "cleanup",
            RabbitConfig.ENRICH_TASK_DLQ, "enrich"
    );

    private final RabbitAdmin rabbitAdmin;
    private final RabbitTemplate rabbitTemplate;

    @Value("${splitter.rabbitmq.dlq.receive-timeout-ms:1500}")
    private long receiveTimeoutMs;

    public List<DlqStatDto> stats() {
        List<DlqStatDto> out = new ArrayList<>();
        for (Map.Entry<String, String> e : DLQ_TO_ROUTING.entrySet()) {
            String queue = e.getKey();
            long count = queueMessageCount(queue);
            out.add(DlqStatDto.builder()
                    .queueName(queue)
                    .targetRoutingKey(e.getValue())
                    .messageCount(count)
                    .build());
        }
        return out;
    }

    public DlqRequeueResultDto requeue(String queueName, int maxMessages) {
        String normalized = queueName == null ? "" : queueName.trim();
        String routingKey = DLQ_TO_ROUTING.get(normalized);
        if (routingKey == null) {
            throw new IllegalArgumentException("Unknown or unsupported DLQ: " + queueName);
        }
        int cap = maxMessages < 1 ? 10_000 : Math.min(maxMessages, 100_000);
        int requeued = 0;
        for (int n = 0; n < cap; n++) {
            Message msg = rabbitTemplate.receive(normalized, receiveTimeoutMs);
            if (msg == null) {
                break;
            }
            rabbitTemplate.send(RabbitConfig.EXCHANGE_NAME, routingKey, msg);
            requeued++;
        }
        long remaining = queueMessageCount(normalized);
        log.info("DLQ 重投: queue={} requeued={} remaining~={}", normalized, requeued, remaining);
        return DlqRequeueResultDto.builder()
                .queueName(normalized)
                .requeued(requeued)
                .remaining(remaining)
                .build();
    }

    private long queueMessageCount(String queueName) {
        Properties props = rabbitAdmin.getQueueProperties(queueName);
        if (props == null) {
            return -1L;
        }
        Object raw = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        if (raw == null) {
            return -1L;
        }
        if (raw instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }
}
