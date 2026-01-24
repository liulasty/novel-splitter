package com.novel.splitter.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/**
 * @author Administrator
 */
@SpringBootApplication
@EnableConfigurationProperties
@ComponentScan("com.novel.splitter")
public class NovelSplitApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovelSplitApplication.class, args);
    }
}
