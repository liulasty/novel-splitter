package com.novel.splitter.llm.client.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.llm.openai.OpenAiMessage;
import com.novel.splitter.domain.model.llm.openai.OpenAiRequest;
import com.novel.splitter.domain.model.llm.openai.OpenAiResponse;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.config.DeepSeekProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * DeepSeek API 客户端实现。
 * 兼容 OpenAI API 格式。
 */
@Slf4j
public class DeepSeekLlmClient implements LlmClient {

    private final RestClient restClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;

    public DeepSeekLlmClient(DeepSeekProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
        this.rateLimiter = new RateLimiter(
                properties.getRateLimit().getMaxRequests(),
                properties.getRateLimit().getDurationSeconds()
        );
        log.info("DeepSeekLlmClient 初始化完成，URL: {}, 模型: {}", properties.getBaseUrl(), properties.getModel());
    }

    @Override
    public Answer chat(Prompt prompt) {
        // 速率限制检查
        rateLimiter.acquire();

        log.info("正在向 DeepSeek 模型发送请求: {}", properties.getModel());

        // 1. 构建消息
        List<OpenAiMessage> messages = new ArrayList<>();

        // 系统消息
        String systemContent = prompt.getSystemInstruction();
        if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
            systemContent += "\n\nIMPORTANT OUTPUT FORMAT:\n" + prompt.getOutputConstraint();
        }
        systemContent += "\n\nYou MUST respond with valid JSON matching the schema provided.";
        messages.add(OpenAiMessage.builder().role("system").content(systemContent).build());

        // 用户消息
        String userContent = buildUserContent(prompt);
        messages.add(OpenAiMessage.builder().role("user").content(userContent).build());

        // 2. 构建请求
        OpenAiRequest request = OpenAiRequest.builder()
                .model(properties.getModel())
                .messages(messages)
                .stream(false)
                .temperature(0.1) // 针对事实型 RAG 使用较低温度
                .response_format(OpenAiRequest.ResponseFormat.builder().type("json_object").build())
                .build();

        try {
            // 3. 调用 API
            OpenAiResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OpenAiResponse.class);

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("Empty response from DeepSeek");
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            log.info("DeepSeek 原始响应: {}", content);

            // 清理 Markdown 代码块
            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            } else if (content.contains("```")) {
                content = content.replace("```", "");
            }
            content = content.trim();
            
            int firstBrace = content.indexOf('{');
            if (firstBrace != -1) {
                content = content.substring(firstBrace);
            }

            // 4. 解析响应
            try (JsonParser parser = objectMapper.createParser(content)) {
                return parser.readValueAs(Answer.class);
            }

        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            throw new RuntimeException("DeepSeek API call failed: " + e.getMessage(), e);
        }
    }

    static String buildUserContent(Prompt prompt) {
        StringBuilder userContent = new StringBuilder();
        if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
            userContent.append("Context Information:\n");
            for (ContextBlock block : prompt.getContextBlocks()) {
                userContent.append("---\n");
                userContent.append("Chunk ID: ").append(block.getChunkId()).append("\n");
                if (block.getSceneMetadata() != null) {
                    userContent.append("Source: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                }
                String chapterTag = block.getSceneMetadata() != null && block.getSceneMetadata().getChapterTitle() != null
                    ? "(" + block.getSceneMetadata().getChapterTitle() + ") "
                    : "";
                userContent.append("Content: ").append(chapterTag).append(block.effectiveContent()).append("\n");
                userContent.append("---\n");
            }
            userContent.append("\n");
        }
        userContent.append("User Question: ").append(prompt.getUserQuestion());
        userContent.append("\n\nPlease answer the question in the specified JSON format.");
        return userContent.toString();
    }

    // 简单令牌桶限流器
    private static class RateLimiter {
        private final int maxRequests;
        private final long durationMillis;
        private final Deque<Long> requestTimestamps = new LinkedList<>();

        public RateLimiter(int maxRequests, int durationSeconds) {
            this.maxRequests = maxRequests;
            this.durationMillis = durationSeconds * 1000L;
        }

        public synchronized void acquire() {
            long now = System.currentTimeMillis();
            
            // 移除过期时间戳
            while (!requestTimestamps.isEmpty() && (now - requestTimestamps.peekFirst() > durationMillis)) {
                requestTimestamps.pollFirst();
            }

            if (requestTimestamps.size() < maxRequests) {
                requestTimestamps.addLast(now);
                return;
            }

            // 如果达到限制，计算等待时间
            long oldestTimestamp = requestTimestamps.peekFirst();
            long waitTime = durationMillis - (now - oldestTimestamp);
            
            if (waitTime > 0) {
                try {
                    log.warn("达到速率限制。等待 {} 毫秒", waitTime);
                    Thread.sleep(waitTime);
                    // 等待后递归重试
                    acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for rate limit", e);
                }
            } else {
                 // 兜底：如果计算异常（如时钟偏移），直接继续以避免无限循环
                 requestTimestamps.pollFirst();
                 requestTimestamps.addLast(now);
            }
        }
    }
}
