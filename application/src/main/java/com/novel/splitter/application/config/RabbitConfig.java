package com.novel.splitter.application.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String SPLIT_TASK_QUEUE = "novel.split.tasks";

    @Bean
    public Queue splitTaskQueue() {
        return new Queue(SPLIT_TASK_QUEUE, true); // durable queue
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
