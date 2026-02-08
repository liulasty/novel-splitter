package com.novel.splitter.llm.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "llm.gemini")
public class GeminiProperties {
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private String apiKey;
    private String model = "gemini-2.5-flash";
    private Integer maxOutputTokens = 8192;
    private RateLimitConfig rateLimit;

    @Data
    public static class RateLimitConfig {
        private boolean enabled = true;
        private int maxRequests = 15; // Gemini Free tier is higher usually (15 RPM)
        private int durationSeconds = 60;
    }
}
