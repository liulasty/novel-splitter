package com.novel.splitter.llm.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.impl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;

/**
 * LLM 客户端配置类
 * <p>
 * 负责初始化各种 LLM 客户端（Ollama, DeepSeek, Gemini, Coze 等）。
 * 并统一配置了超时重试机制，增强系统在网络波动或服务不稳定时的鲁棒性。
 * </p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({OllamaProperties.class, DeepSeekProperties.class, GeminiProperties.class, CozeProperties.class})
public class LlmClientConfig {

    /**
     * 配置统一的重试模板
     * <p>
     * 重试策略：
     * 1. 最大重试次数：3次
     * 2. 回退策略：指数回退（初始间隔 1s，倍率 2.0，最大间隔 10s）
     * </p>
     *
     * @return 预配置的 RetryTemplate 实例
     */
    @Bean
    public RetryTemplate llmRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // 1. 简单重试策略：最多尝试 3 次（包含第 1 次）
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);

        // 2. 指数回退策略：避免瞬间流量冲击
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000L); // 初始间隔 1 秒
        backOffPolicy.setMultiplier(2.0);        // 每次间隔翻倍
        backOffPolicy.setMaxInterval(10000L);    // 最大间隔 10 秒
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "mock", matchIfMissing = true)
    public LlmClient mockLlmClient(RetryTemplate retryTemplate) {
        log.info("初始化 Mock LLM 客户端...");
        return new RetryableLlmClient(new MockLlmClient(), retryTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(
            OllamaProperties ollamaProperties,
            ObjectMapper objectMapper,
            RetryTemplate retryTemplate) {
        log.info("初始化 Ollama LLM 客户端...");
        return new RetryableLlmClient(new OllamaLlmClient(ollamaProperties, objectMapper), retryTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "deepseek")
    public LlmClient deepSeekLlmClient(
            DeepSeekProperties deepSeekProperties,
            ObjectMapper objectMapper,
            RetryTemplate retryTemplate) {
        log.info("初始化 DeepSeek LLM 客户端...");
        return new RetryableLlmClient(new DeepSeekLlmClient(deepSeekProperties, objectMapper), retryTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "gemini")
    public LlmClient geminiLlmClient(
            GeminiProperties geminiProperties,
            ObjectMapper objectMapper,
            RetryTemplate retryTemplate) {
        log.info("初始化 Gemini LLM 客户端...");
        return new RetryableLlmClient(new GeminiLlmClient(geminiProperties, objectMapper), retryTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "coze")
    public LlmClient cozeLlmClient(
            CozeProperties cozeProperties,
            ObjectMapper objectMapper,
            RetryTemplate retryTemplate) {
        log.info("初始化 Coze LLM 客户端...");
        return new RetryableLlmClient(new CozeLlmClient(cozeProperties, objectMapper), retryTemplate);
    }

    /**
     * 带有自动重试功能的 LLM 客户端装饰器
     * <p>
     * 包装原始客户端，当发生异常时利用 RetryTemplate 进行自动重试。
     * </p>
     */
    @RequiredArgsConstructor
    static class RetryableLlmClient implements LlmClient {
        private final LlmClient delegate;
        private final RetryTemplate retryTemplate;

        @Override
        public Answer chat(Prompt prompt) {
            return retryTemplate.execute(context -> {
                if (context.getRetryCount() > 0) {
                    log.warn("LLM 请求失败，正在进行第 {} 次重试...", context.getRetryCount());
                }
                return delegate.chat(prompt);
            });
        }
    }
}
