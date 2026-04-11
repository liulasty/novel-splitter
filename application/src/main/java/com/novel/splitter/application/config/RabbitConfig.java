package com.novel.splitter.application.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

@Configuration
public class RabbitConfig {
    @Value("${splitter.rabbitmq.retry.max-attempts:3}")
    private int retryMaxAttempts;

    @Value("${splitter.rabbitmq.retry.initial-interval-ms:1000}")
    private long retryInitialIntervalMs;

    @Value("${splitter.rabbitmq.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${splitter.rabbitmq.retry.max-interval-ms:10000}")
    private long retryMaxIntervalMs;

    @Value("${splitter.embed.scene-listener.batch-size:50}")
    private int embedSceneBatchSize;

    /**
     * embed.scene 批量监听器在「整批投递」失败时的最大尝试次数（含首次）。默认 1：不在 MQ 层对同一批做多次重试，
     * 避免 Chroma/模型等硬错误反复执行；单场景失败由 {@code EmbedWorker} 写入 {@code scenes.embed_error} 并由进度调度汇总。
     */
    @Value("${splitter.embed.scene-listener.listener-max-attempts:1}")
    private int embedSceneListenerMaxAttempts;

    public static final String EXCHANGE_NAME = "novel.task.exchange";

    public static final String LOAD_TASK_QUEUE = "novel.task.load";
    public static final String SPLIT_TASK_QUEUE = "novel.task.split";
    public static final String EMBED_TASK_QUEUE = "novel.task.embed";
    /** 细粒度：单场景向量化子任务 */
    public static final String EMBED_SCENE_TASK_QUEUE = "novel.task.embed.scene";
    public static final String EMBED_SCENE_ROUTING_KEY = "embed.scene";
    public static final String CLEANUP_TASK_QUEUE = "novel.task.cleanup";
    public static final String ENRICH_TASK_QUEUE = "novel.task.enrich";
    public static final String DLX_EXCHANGE_NAME = "novel.task.dlx";

    public static final String LOAD_TASK_DLQ = "novel.task.load.dlq";
    public static final String SPLIT_TASK_DLQ = "novel.task.split.dlq";
    public static final String EMBED_TASK_DLQ = "novel.task.embed.dlq";
    public static final String CLEANUP_TASK_DLQ = "novel.task.cleanup.dlq";
    public static final String ENRICH_TASK_DLQ = "novel.task.enrich.dlq";

    @Bean
    public DirectExchange taskExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE_NAME);
    }

    @Bean
    public Queue loadTaskQueue() {
        return new Queue(LOAD_TASK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE_NAME,
                "x-dead-letter-routing-key", "load.dlq"
        ));
    }

    @Bean
    public Queue splitTaskQueue() {
        return new Queue(SPLIT_TASK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE_NAME,
                "x-dead-letter-routing-key", "split.dlq"
        ));
    }

    @Bean
    public Queue embedTaskQueue() {
        return new Queue(EMBED_TASK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE_NAME,
                "x-dead-letter-routing-key", "embed.dlq"
        ));
    }

    @Bean
    public Queue embedSceneTaskQueue() {
        return new Queue(EMBED_SCENE_TASK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE_NAME,
                "x-dead-letter-routing-key", "embed.dlq"
        ));
    }

    @Bean
    public Queue cleanupTaskQueue() {
        return new Queue(CLEANUP_TASK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE_NAME,
                "x-dead-letter-routing-key", "cleanup.dlq"
        ));
    }

    @Bean
    public Queue enrichTaskQueue() {
        return new Queue(ENRICH_TASK_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", DLX_EXCHANGE_NAME,
                "x-dead-letter-routing-key", "enrich.dlq"
        ));
    }

    @Bean
    public Queue loadTaskDlq() {
        return new Queue(LOAD_TASK_DLQ, true);
    }

    @Bean
    public Queue splitTaskDlq() {
        return new Queue(SPLIT_TASK_DLQ, true);
    }

    @Bean
    public Queue embedTaskDlq() {
        return new Queue(EMBED_TASK_DLQ, true);
    }

    @Bean
    public Queue cleanupTaskDlq() {
        return new Queue(CLEANUP_TASK_DLQ, true);
    }

    @Bean
    public Queue enrichTaskDlq() {
        return new Queue(ENRICH_TASK_DLQ, true);
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
    public Binding embedSceneBinding(Queue embedSceneTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(embedSceneTaskQueue).to(taskExchange).with(EMBED_SCENE_ROUTING_KEY);
    }

    @Bean
    public Binding cleanupBinding(Queue cleanupTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(cleanupTaskQueue).to(taskExchange).with("cleanup");
    }

    @Bean
    public Binding enrichBinding(Queue enrichTaskQueue, DirectExchange taskExchange) {
        return BindingBuilder.bind(enrichTaskQueue).to(taskExchange).with("enrich");
    }

    @Bean
    public Binding loadDlqBinding(Queue loadTaskDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(loadTaskDlq).to(deadLetterExchange).with("load.dlq");
    }

    @Bean
    public Binding splitDlqBinding(Queue splitTaskDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(splitTaskDlq).to(deadLetterExchange).with("split.dlq");
    }

    @Bean
    public Binding embedDlqBinding(Queue embedTaskDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(embedTaskDlq).to(deadLetterExchange).with("embed.dlq");
    }

    @Bean
    public Binding cleanupDlqBinding(Queue cleanupTaskDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(cleanupTaskDlq).to(deadLetterExchange).with("cleanup.dlq");
    }

    @Bean
    public Binding enrichDlqBinding(Queue enrichTaskDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(enrichTaskDlq).to(deadLetterExchange).with("enrich.dlq");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    /**
     * Declared here so {@code DlqService} and queue introspection work even when Boot's
     * {@code RabbitAdmin} auto-configuration does not register a bean (e.g. conditional mismatch).
     */
    @Bean
    @ConditionalOnMissingBean(RabbitAdmin.class)
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RetryOperationsInterceptor rabbitRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(retryMaxAttempts)
                .backOffOptions(retryInitialIntervalMs, retryMultiplier, retryMaxIntervalMs)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public RetryOperationsInterceptor embedSceneRabbitRetryInterceptor() {
        int attempts = Math.max(1, embedSceneListenerMaxAttempts);
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(attempts)
                .backOffOptions(retryInitialIntervalMs, retryMultiplier, retryMaxIntervalMs)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor rabbitRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(rabbitRetryInterceptor);
        return factory;
    }

    /**
     * 细粒度 embed 批量消费（与全局 {@link #rabbitListenerContainerFactory} 隔离，避免 batch 影响其它队列）。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory embedSceneBatchListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor embedSceneRabbitRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(embedSceneRabbitRetryInterceptor);
        factory.setConsumerBatchEnabled(true);
        factory.setBatchListener(true);
        factory.setBatchSize(Math.max(1, embedSceneBatchSize));
        return factory;
    }
}
