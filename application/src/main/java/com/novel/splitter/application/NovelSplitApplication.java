package com.novel.splitter.application;

import io.github.cdimascio.dotenv.Dotenv;
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
        // Load .env file variables into System properties
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

        SpringApplication.run(NovelSplitApplication.class, args);
    }
}
