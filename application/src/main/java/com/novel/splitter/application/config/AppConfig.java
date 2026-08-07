package com.novel.splitter.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "splitter")
public class AppConfig {
    private StorageConfig storage;
    private RuleConfig rule;

    @Data
    public static class StorageConfig {
        private String rootPath;
        /**
         * novel-raw/{novelId}/original.txt
         */
        private String rawDirName = "novel-raw";
        /**
         * novel-parsed/{novelId}/chapter_{index}.json
         */
        private String parsedDirName = "novel-parsed";
        private String rawFilename = "original.txt";
    }

    @Data
    public static class RuleConfig {
        private int targetLength;
        private int minLength;
        private int maxLength;
        private boolean ignoreEmptyLines;
    }
}
