package com.novel.splitter.llm.client;

import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.config.LlmClientConfig;
import com.novel.splitter.llm.client.impl.MockLlmClient;
import com.novel.splitter.llm.client.impl.OllamaLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = LlmClientConfig.class)
@EnableAutoConfiguration
@TestPropertySource(properties = {
        "novel.llm.provider=ollama",
        "llm.ollama.url=http://test-url",
        "llm.ollama.model=test-model"
})
class OllamaClientTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LlmClient llmClient;

    @Test
    void shouldLoadOllamaClient() {
        assertThat(llmClient).isInstanceOf(OllamaLlmClient.class);
        assertThat(applicationContext.getBeansOfType(MockLlmClient.class)).isEmpty();
    }
}
