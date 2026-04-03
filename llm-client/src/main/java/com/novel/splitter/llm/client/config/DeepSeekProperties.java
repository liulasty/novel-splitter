package com.novel.splitter.llm.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "llm.deepseek")
public class DeepSeekProperties {
    private String baseUrl = "https://api.deepseek.com";
    private String apiKey;
    private String model = "deepseek-chat";
    private RateLimitConfig rateLimit;

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private int maxRequests = 3;
        private int durationSeconds = 60;
    }
}
