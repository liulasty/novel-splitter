package com.novel.splitter.llm.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.impl.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM 客户端配置
 * <p>
 * 支持通过配置切换 Mock 和真实实现。
 * 默认使用 Mock 实现，以便在离线环境下运行。
 * </p>
 */
@Configuration
@EnableConfigurationProperties({OllamaProperties.class, DeepSeekProperties.class, GeminiProperties.class, CozeProperties.class})
public class LlmClientConfig {

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "mock", matchIfMissing = true)
    public LlmClient mockLlmClient() {
        return new MockLlmClient();
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(
            OllamaProperties ollamaProperties,
            ObjectMapper objectMapper) {
        return new OllamaLlmClient(ollamaProperties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "deepseek")
    public LlmClient deepSeekLlmClient(
            DeepSeekProperties deepSeekProperties,
            ObjectMapper objectMapper) {
        return new DeepSeekLlmClient(deepSeekProperties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "gemini")
    public LlmClient geminiLlmClient(
            GeminiProperties geminiProperties,
            ObjectMapper objectMapper) {
        return new GeminiLlmClient(geminiProperties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "coze")
    public LlmClient cozeLlmClient(
            CozeProperties cozeProperties,
            ObjectMapper objectMapper) {
        return new CozeLlmClient(cozeProperties, objectMapper);
    }
}
