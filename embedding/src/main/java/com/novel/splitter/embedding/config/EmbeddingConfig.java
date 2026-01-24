package com.novel.splitter.embedding.config;

import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import com.novel.splitter.embedding.mock.MockEmbeddingService;
import com.novel.splitter.embedding.mock.MockVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Bean
    @ConditionalOnMissingBean
    public EmbeddingService embeddingService() {
        return new MockEmbeddingService(768); // Default dimension
    }

    @Bean
    @ConditionalOnMissingBean
    public VectorStore vectorStore() {
        return new MockVectorStore();
    }
}
