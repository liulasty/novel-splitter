package com.novel.splitter.embedding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.novel.splitter.embedding")
public class EmbeddingTestConfig {
    public static void main(String[] args) {
        SpringApplication.run(EmbeddingTestConfig.class, args);
    }
}
