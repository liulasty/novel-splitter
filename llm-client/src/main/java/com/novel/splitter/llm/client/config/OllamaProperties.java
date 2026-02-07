package com.novel.splitter.llm.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@ConfigurationProperties(prefix = "llm.ollama")
public class OllamaProperties {
    private String url;
    private String model;
    private OptionsConfig options;

    @Data
    public static class OptionsConfig {
        private Integer numCtx;
        private Integer numThreads;
        private Double temperature;
        private Integer maxTokens;
        private String format;
        private Integer numGpu;
    }
}
