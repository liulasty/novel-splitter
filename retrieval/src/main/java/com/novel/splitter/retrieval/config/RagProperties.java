package com.novel.splitter.retrieval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "splitter.rag")
public class RagProperties {
    private String systemInstruction;
    private String outputConstraint;
    private int defaultTopK = 5;
    private double minConfidence = 0.5;
    private int maxRetries = 2;
}
