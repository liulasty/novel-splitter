package com.novel.splitter.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "splitter")
public class AppConfig {
    private StorageConfig storage;
    private RuleConfig rule;
    private DownloaderConfig downloader;

    @Data
    public static class StorageConfig {
        private String rootPath;
    }

    @Data
    public static class RuleConfig {
        private int targetLength;
        private int minLength;
        private int maxLength;
        private boolean ignoreEmptyLines;
    }

    @Data
    public static class DownloaderConfig {
        private int threadCount;
        private int timeoutMs;
        private int retryCount;
        private List<SiteConfig> sites;
    }

    @Data
    public static class SiteConfig {
        private String domain;
        private String catalogUrl;
        private String chapterListSelector;
        private String contentSelector;
        private String nextPageSelector;
    }
}
