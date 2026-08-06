package com.novel.splitter.llm.client.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.llm.coze.*;
import com.novel.splitter.llm.client.api.LlmClient;
import com.novel.splitter.llm.client.config.CozeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Coze API 客户端实现。
 * 使用 Coze v3 Chat API（非流式 + 轮询）。
 */
@Slf4j
public class CozeLlmClient implements LlmClient {

    private final RestClient restClient;
    private final CozeProperties properties;
    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;

    public CozeLlmClient(CozeProperties properties, ObjectMapper objectMapper) {
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
        log.info("CozeLlmClient 初始化完成，URL: {}, botId: {}", properties.getBaseUrl(), properties.getBotId());
    }

    @Override
    public Answer chat(Prompt prompt) {
        if (properties.getBotId() == null || properties.getBotId().trim().isEmpty()) {
            throw new IllegalArgumentException("Coze Bot ID is not configured. Please set COZE_BOT_ID in .env or application.yml");
        }

        // 速率限制检查
        rateLimiter.acquire();

        log.info("正在为 Bot ID: {} 启动 Coze 对话会话", properties.getBotId());

        // 1. 构建消息内容
        // Coze 机器人有自己的系统提示词，但我们需要注入我们的 RAG 上下文和指令。
        // 我们会把所有内容合并成一条用户消息，确保其被处理。
        StringBuilder fullContent = new StringBuilder();

        // 系统指令部分
        fullContent.append("[System Instruction]\n");
        fullContent.append(prompt.getSystemInstruction());
        if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
            fullContent.append("\n\nIMPORTANT OUTPUT FORMAT:\n").append(prompt.getOutputConstraint());
        }
        fullContent.append("\n\nYou MUST respond with valid JSON matching the schema provided.\n\n");

        // 上下文部分
        if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
            fullContent.append("[Context Information]\n");
            for (ContextBlock block : prompt.getContextBlocks()) {
                fullContent.append("---\n");
                fullContent.append("Chunk ID: ").append(block.getChunkId()).append("\n");
                if (block.getSceneMetadata() != null) {
                    fullContent.append("Source: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                }
                String chapterTag = block.getSceneMetadata() != null && block.getSceneMetadata().getChapterTitle() != null
                    ? "(" + block.getSceneMetadata().getChapterTitle() + ") "
                    : "";
                fullContent.append("Content: ").append(chapterTag).append(block.effectiveContent()).append("\n");
                fullContent.append("---\n");
            }
            fullContent.append("\n");
        }

        // 问题部分
        fullContent.append("[User Question]\n").append(prompt.getUserQuestion());
        fullContent.append("\n\nPlease answer the question in the specified JSON format.");

        // 2. 创建对话
        CozeChatRequest request = CozeChatRequest.builder()
                .bot_id(properties.getBotId())
                .user_id(properties.getUserId())
                .stream(false)
                .auto_save_history(true)
                .additional_messages(Collections.singletonList(
                        CozeMessage.builder()
                                .role("user")
                                .content(fullContent.toString())
                                .content_type("text")
                                .build()
                ))
                .build();

        try {
            CozeChatResponse chatResponse = restClient.post()
                    .uri("/v3/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(CozeChatResponse.class);

            if (chatResponse == null) {
                throw new RuntimeException("Failed to create Coze chat: Null response from API");
            }

            if (chatResponse.getCode() != null && chatResponse.getCode() != 0) {
                 throw new RuntimeException("Coze API Error: Code=" + chatResponse.getCode() + ", Msg=" + chatResponse.getMsg());
            }

            if (chatResponse.getData() == null) {
                // 如果 code 为 0 但 data 为空，对成功响应而言属于意外情况
                throw new RuntimeException("Failed to create Coze chat: Success code (0) but empty data. Full response: " + chatResponse);
            }

            String chatId = chatResponse.getData().getId();
            String conversationId = chatResponse.getData().getConversation_id();
            
            log.info("Coze 对话已创建，ID: {}, 状态: {}", chatId, chatResponse.getData().getStatus());

            // 3. 轮询等待完成
            waitForCompletion(chatId, conversationId);

            // 4. 获取消息
            return retrieveAnswer(chatId, conversationId);

        } catch (Exception e) {
            log.error("Coze API 调用失败", e);
            throw new RuntimeException("Coze API call failed: " + e.getMessage(), e);
        }
    }

    private void waitForCompletion(String chatId, String conversationId) {
        String status = "in_progress";
        int maxRetries = properties.getTimeoutSeconds(); 
        int attempt = 0;

        while (!"completed".equals(status) && attempt < maxRetries) {
            try {
                TimeUnit.SECONDS.sleep(1); // 每 1 秒轮询一次
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for Coze response");
            }

            try {
                CozeChatResponse pollResponse = restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/v3/chat/retrieve")
                                .queryParam("chat_id", chatId)
                                .queryParam("conversation_id", conversationId)
                                .build())
                        .retrieve()
                        .body(CozeChatResponse.class);

                if (pollResponse != null && pollResponse.getData() != null) {
                    status = pollResponse.getData().getStatus();
                    if ("failed".equals(status) || "requires_action".equals(status)) {
                        throw new RuntimeException("Coze Chat failed with status: " + status + ", error: " + pollResponse.getData().getLast_error());
                    }
                }
            } catch (Exception e) {
                log.warn("轮询 Coze 状态失败: {}", e.getMessage());
            }
            attempt++;
        }

        if (!"completed".equals(status)) {
            throw new RuntimeException("Coze Chat timed out after " + maxRetries + " seconds");
        }
    }

    private Answer retrieveAnswer(String chatId, String conversationId) {
        CozeMessageListResponse listResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v3/chat/message/list")
                        .queryParam("chat_id", chatId)
                        .queryParam("conversation_id", conversationId)
                        .build())
                .retrieve()
                .body(CozeMessageListResponse.class);

        if (listResponse == null || listResponse.getData() == null) {
            throw new RuntimeException("Failed to retrieve Coze messages");
        }

        // 查找助手的回答
        String answerContent = listResponse.getData().stream()
                .filter(msg -> "assistant".equals(msg.getRole()) && "answer".equals(msg.getType()))
                .map(CozeMessageListResponse.CozeMessageDetail::getContent)
                .reduce("", (a, b) -> a + b); // 如有多个部分则合并

        log.info("Coze 原始响应内容: {}", answerContent);

        // 清理 Markdown
        if (answerContent.contains("```json")) {
            answerContent = answerContent.replace("```json", "").replace("```", "");
        } else if (answerContent.contains("```")) {
            answerContent = answerContent.replace("```", "");
        }
        answerContent = answerContent.trim();

        int firstBrace = answerContent.indexOf('{');
        if (firstBrace != -1) {
            answerContent = answerContent.substring(firstBrace);
        }

        try (JsonParser parser = objectMapper.createParser(answerContent)) {
            return parser.readValueAs(Answer.class);
        } catch (Exception e) {
            log.error("从 Coze 响应解析 JSON 失败: {}", answerContent);
            throw new RuntimeException("Invalid JSON from Coze: " + e.getMessage(), e);
        }
    }

    // 限流器
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
            while (!requestTimestamps.isEmpty() && (now - requestTimestamps.peekFirst() > durationMillis)) {
                requestTimestamps.pollFirst();
            }
            if (requestTimestamps.size() < maxRequests) {
                requestTimestamps.addLast(now);
                return;
            }
            long waitTime = durationMillis - (now - requestTimestamps.peekFirst());
            if (waitTime > 0) {
                try {
                    Thread.sleep(waitTime);
                    acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                 requestTimestamps.pollFirst();
                 requestTimestamps.addLast(now);
            }
        }
    }
}
