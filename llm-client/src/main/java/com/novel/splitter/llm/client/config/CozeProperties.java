package com.novel.splitter.llm.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "llm.coze")
public class CozeProperties {
    private String baseUrl = "https://api.coze.com";
    private String apiKey;
    private String botId;
    private String userId = "user_default";
    private int timeoutSeconds = 300; // 默认超时时间：5 分钟
    private RateLimitConfig rateLimit;

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private int maxRequests = 2; // Coze 免费套餐的限流可能较严格
        private int durationSeconds = 60;
    }
}
