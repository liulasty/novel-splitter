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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Coze API Client Implementation.
 * Uses Coze v3 Chat API (Non-streaming with Polling).
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
        log.info("Initialized CozeLlmClient with url: {}, botId: {}", properties.getBaseUrl(), properties.getBotId());
    }

    @Override
    public Answer chat(Prompt prompt) {
        if (properties.getBotId() == null || properties.getBotId().trim().isEmpty()) {
            throw new IllegalArgumentException("Coze Bot ID is not configured. Please set COZE_BOT_ID in .env or application.yml");
        }

        // Rate Limit Check
        rateLimiter.acquire();

        log.info("Starting Coze chat session for Bot ID: {}", properties.getBotId());

        // 1. Construct Message Content
        // Coze bots have their own system prompt, but we need to inject our RAG context and instructions.
        // We will combine everything into a single User message to ensure it's processed.
        StringBuilder fullContent = new StringBuilder();
        
        // System Instruction Part
        fullContent.append("[System Instruction]\n");
        fullContent.append(prompt.getSystemInstruction());
        if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
            fullContent.append("\n\nIMPORTANT OUTPUT FORMAT:\n").append(prompt.getOutputConstraint());
        }
        fullContent.append("\n\nYou MUST respond with valid JSON matching the schema provided.\n\n");

        // Context Part
        if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
            fullContent.append("[Context Information]\n");
            for (ContextBlock block : prompt.getContextBlocks()) {
                fullContent.append("---\n");
                fullContent.append("Chunk ID: ").append(block.getChunkId()).append("\n");
                if (block.getSceneMetadata() != null) {
                    fullContent.append("Source: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                }
                fullContent.append("Content: ").append(block.getContent()).append("\n");
                fullContent.append("---\n");
            }
            fullContent.append("\n");
        }

        // Question Part
        fullContent.append("[User Question]\n").append(prompt.getUserQuestion());
        fullContent.append("\n\nPlease answer the question in the specified JSON format.");

        // 2. Create Chat
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
                // If code is 0 but data is null, that's unexpected for a success response
                throw new RuntimeException("Failed to create Coze chat: Success code (0) but empty data. Full response: " + chatResponse);
            }

            String chatId = chatResponse.getData().getId();
            String conversationId = chatResponse.getData().getConversation_id();
            
            log.info("Coze Chat created. ID: {}, Status: {}", chatId, chatResponse.getData().getStatus());

            // 3. Poll for Completion
            waitForCompletion(chatId, conversationId);

            // 4. Retrieve Messages
            return retrieveAnswer(chatId, conversationId);

        } catch (Exception e) {
            log.error("Coze API call failed", e);
            throw new RuntimeException("Coze API call failed: " + e.getMessage(), e);
        }
    }

    private void waitForCompletion(String chatId, String conversationId) {
        String status = "in_progress";
        int maxRetries = properties.getTimeoutSeconds(); 
        int attempt = 0;

        while (!"completed".equals(status) && attempt < maxRetries) {
            try {
                TimeUnit.SECONDS.sleep(1); // Poll every 1 second
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
                log.warn("Failed to poll Coze status: {}", e.getMessage());
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

        // Find the assistant's answer
        String answerContent = listResponse.getData().stream()
                .filter(msg -> "assistant".equals(msg.getRole()) && "answer".equals(msg.getType()))
                .map(CozeMessageListResponse.CozeMessageDetail::getContent)
                .reduce("", (a, b) -> a + b); // Combine parts if any

        log.info("Coze raw response content: {}", answerContent);
        
        // Clean up Markdown
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
            log.error("Failed to parse JSON from Coze response: {}", answerContent);
            throw new RuntimeException("Invalid JSON from Coze: " + e.getMessage(), e);
        }
    }

    // Rate Limiter
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
