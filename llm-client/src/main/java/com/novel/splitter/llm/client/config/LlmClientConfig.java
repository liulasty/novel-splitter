package com.novel.splitter.llm.client.config;

import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.impl.MockLlmClient;
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

    // 未来可以在这里添加 OpenAiLlmClient 等实现
    // @Bean
    // @ConditionalOnProperty(name = "novel.llm.provider", havingValue = "openai")
    // public LlmClient openAiLlmClient() { ... }
}
