package com.novel.splitter.llm.client.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.genai.Client;
import com.google.genai.Client.Builder;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.config.GeminiProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Gemini API 客户端实现。
 * 使用 Google AI Gemini API (google-genai SDK)。
 */
@Slf4j
public class GeminiLlmClient implements LlmClient {

    private final Client client;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;

    public GeminiLlmClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        
        // 使用 Builder 初始化客户端
        Builder builder = Client.builder()
                .apiKey(properties.getApiKey());

        // 配置 HTTP 选项（超时时间默认设置为 60秒，避免连接挂起）
        HttpOptions.Builder httpOptionsBuilder = HttpOptions.builder();
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isEmpty()) {
            httpOptionsBuilder.baseUrl(properties.getBaseUrl());
        }
        // 设置超时时间（毫秒），这里设置为 60 秒
        httpOptionsBuilder.timeout(60000);
        
        builder.httpOptions(httpOptionsBuilder.build());

        this.client = builder.build();

        this.rateLimiter = new RateLimiter(
                properties.getRateLimit().getMaxRequests(),
                properties.getRateLimit().getDurationSeconds()
        );
        log.info("GeminiLlmClient 初始化完成，模型: {}", properties.getModel());
    }

    @Override
    public Answer chat(Prompt prompt) {
        // 速率限制检查
        rateLimiter.acquire();

        log.info("正在向 Gemini 模型发送请求: {}", properties.getModel());

        try {
            // 1. 构建用户内容
            StringBuilder userContentBuilder = new StringBuilder();
            if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
                userContentBuilder.append("上下文信息:\n");
                for (ContextBlock block : prompt.getContextBlocks()) {
                    userContentBuilder.append("---\n");
                    userContentBuilder.append("块 ID: ").append(block.getChunkId()).append("\n");
                    if (block.getSceneMetadata() != null) {
                        userContentBuilder.append("来源: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                    }
                    userContentBuilder.append("内容: ").append(block.getContent()).append("\n");
                    userContentBuilder.append("---\n");
                }
                userContentBuilder.append("\n");
            }
            userContentBuilder.append("用户问题: ").append(prompt.getUserQuestion());
            userContentBuilder.append("\n\n请以指定的 JSON 格式回答问题。");

            // 2. 构建系统指令
            String systemText = prompt.getSystemInstruction();
            if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
                systemText += "\n\n重要输出格式:\n" + prompt.getOutputConstraint();
            }
            systemText += "\n\n你必须回复符合提供的 Schema 的有效 JSON。";
            
            Content systemInstructionContent = Content.fromParts(Part.fromText(systemText));
            
            // 3. 配置生成选项
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .temperature(0.1f)
                    .maxOutputTokens(properties.getMaxOutputTokens())
                    .systemInstruction(systemInstructionContent)
                    .build();

            // 4. 调用 API
            GenerateContentResponse response = client.models.generateContent(
                    properties.getModel(),
                    Content.fromParts(Part.fromText(userContentBuilder.toString())),
                    config
            );

            String content = response.text();
            log.info("Gemini 原始响应: {}", content);

            if (content == null || content.isEmpty()) {
                throw new RuntimeException("Gemini 响应为空");
            }

            // 清理 Markdown 代码块（如果存在）
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

            // 5. 解析响应
            try (JsonParser parser = objectMapper.createParser(content)) {
                return parser.readValueAs(Answer.class);
            }

        } catch (Exception e) {
            log.error("Gemini API 调用失败", e);
            throw new RuntimeException("Gemini API 调用失败: " + e.getMessage(), e);
        }
    }

    // 简单令牌桶限流器（复制自 DeepSeekLlmClient 以保持独立性）
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
                    throw new RuntimeException("等待速率限制时被打断", e);
                }
            } else {
                 requestTimestamps.pollFirst();
                 requestTimestamps.addLast(now);
            }
        }
    }
}
