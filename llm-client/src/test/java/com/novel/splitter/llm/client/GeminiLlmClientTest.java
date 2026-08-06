package com.novel.splitter.llm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.llm.client.config.GeminiProperties;
import com.novel.splitter.llm.client.impl.GeminiLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
public class GeminiLlmClientTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".*")
    public void testConnectivity() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        log.info("开始 Gemini 连通性测试，API Key: {}...", apiKey.substring(0, 5) + "***");

        // 1. 配置属性
        GeminiProperties properties = new GeminiProperties();
        properties.setApiKey(apiKey);
        properties.setModel("gemini-2.5-flash");
        
        GeminiProperties.RateLimitConfig rateLimit = new GeminiProperties.RateLimitConfig();
        rateLimit.setMaxRequests(10);
        rateLimit.setDurationSeconds(60);
        properties.setRateLimit(rateLimit);

        // 2. 初始化客户端
        ObjectMapper objectMapper = new ObjectMapper();
        GeminiLlmClient client = new GeminiLlmClient(properties, objectMapper);

        // 3. 准备提示词
        Prompt prompt = Prompt.builder()
                .systemInstruction("You are a helpful assistant. You strictly output JSON.")
                .userQuestion("Hello, please reply with a valid Answer JSON. Just say 'Hello World' in the answer field.")
                .build();

        // 4. 执行对话
        log.info("正在发送请求...");
        try {
            Answer answer = client.chat(prompt);
            log.info("收到响应: {}", answer);

            // 5. 验证
            assertNotNull(answer);
            assertNotNull(answer.getAnswer());
            log.info("连通性测试通过");
        } catch (Exception e) {
            log.error("连通性测试失败", e);
            throw e;
        }
    }
}
