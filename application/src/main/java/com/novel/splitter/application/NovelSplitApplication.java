package com.novel.splitter.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author Administrator
 */
@SpringBootApplication
@EnableConfigurationProperties
public class NovelSplitApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelSplitApplication.class, args);
    }
}
