package com.novel.splitter.llm.client.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.impl.MockLlmClient;
import com.novel.splitter.llm.client.impl.OllamaLlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
public class LlmClientConfig {

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "mock", matchIfMissing = true)
    public LlmClient mockLlmClient() {
        return new MockLlmClient();
    }

    @Bean
    @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(
            @Value("${llm.ollama.url:http://localhost:11434}") String ollamaUrl,
            @Value("${llm.ollama.model:qwen2.5:7b}") String modelName,
            ObjectMapper objectMapper) {
        return new OllamaLlmClient(ollamaUrl, modelName, objectMapper);
    }
}
