package com.novel.splitter.application.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_NAME = "novel.task.exchange";

    public static final String LOAD_TASK_QUEUE = "novel.task.load";
    public static final String SPLIT_TASK_QUEUE = "novel.task.split";
    public static final String EMBED_TASK_QUEUE = "novel.task.embed";
    public static final String CLEANUP_TASK_QUEUE = "novel.task.cleanup";

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue loadTaskQueue() {
        return new Queue(LOAD_TASK_QUEUE, true);
    }

    @Bean
    public Queue splitTaskQueue() {
        return new Queue(SPLIT_TASK_QUEUE, true);
    }

    @Bean
    public Queue embedTaskQueue() {
        return new Queue(EMBED_TASK_QUEUE, true);
    }

    @Bean
    public Queue cleanupTaskQueue() {
        return new Queue(CLEANUP_TASK_QUEUE, true);
    }

    @Bean
    public Binding loadBinding(Queue loadTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(loadTaskQueue).to(taskExchange).with("load");
    }

    @Bean
    public Binding splitBinding(Queue splitTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(splitTaskQueue).to(taskExchange).with("split");
    }

    @Bean
    public Binding embedBinding(Queue embedTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(embedTaskQueue).to(taskExchange).with("embed");
    }

    @Bean
    public Binding cleanupBinding(Queue cleanupTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(cleanupTaskQueue).to(taskExchange).with("cleanup");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
