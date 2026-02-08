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
 * DeepSeek API Client Implementation.
 * Compatible with OpenAI API format.
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
        log.info("Initialized DeepSeekLlmClient with url: {}, model: {}", properties.getBaseUrl(), properties.getModel());
    }

    @Override
    public Answer chat(Prompt prompt) {
        // Rate Limit Check
        rateLimiter.acquire();

        log.info("Sending request to DeepSeek model: {}", properties.getModel());

        // 1. Construct messages
        List<OpenAiMessage> messages = new ArrayList<>();

        // System message
        String systemContent = prompt.getSystemInstruction();
        if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
            systemContent += "\n\nIMPORTANT OUTPUT FORMAT:\n" + prompt.getOutputConstraint();
        }
        systemContent += "\n\nYou MUST respond with valid JSON matching the schema provided.";
        messages.add(OpenAiMessage.builder().role("system").content(systemContent).build());

        // User message
        StringBuilder userContent = new StringBuilder();
        if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
            userContent.append("Context Information:\n");
            for (ContextBlock block : prompt.getContextBlocks()) {
                userContent.append("---\n");
                userContent.append("Chunk ID: ").append(block.getChunkId()).append("\n");
                if (block.getSceneMetadata() != null) {
                    userContent.append("Source: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                }
                userContent.append("Content: ").append(block.getContent()).append("\n");
                userContent.append("---\n");
            }
            userContent.append("\n");
        }
        userContent.append("User Question: ").append(prompt.getUserQuestion());
        userContent.append("\n\nPlease answer the question in the specified JSON format.");

        messages.add(OpenAiMessage.builder().role("user").content(userContent.toString()).build());

        // 2. Build Request
        OpenAiRequest request = OpenAiRequest.builder()
                .model(properties.getModel())
                .messages(messages)
                .stream(false)
                .temperature(0.1) // Low temperature for factual RAG
                .response_format(OpenAiRequest.ResponseFormat.builder().type("json_object").build())
                .build();

        try {
            // 3. Call API
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
            log.info("DeepSeek raw response: {}", content);

            // Clean up Markdown code blocks
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

            // 4. Parse Response
            try (JsonParser parser = objectMapper.createParser(content)) {
                return parser.readValueAs(Answer.class);
            }

        } catch (Exception e) {
            log.error("DeepSeek API call failed", e);
            throw new RuntimeException("DeepSeek API call failed: " + e.getMessage(), e);
        }
    }

    // Simple Token Bucket Rate Limiter
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
            
            // Remove expired timestamps
            while (!requestTimestamps.isEmpty() && (now - requestTimestamps.peekFirst() > durationMillis)) {
                requestTimestamps.pollFirst();
            }

            if (requestTimestamps.size() < maxRequests) {
                requestTimestamps.addLast(now);
                return;
            }

            // If limit reached, calculate wait time
            long oldestTimestamp = requestTimestamps.peekFirst();
            long waitTime = durationMillis - (now - oldestTimestamp);
            
            if (waitTime > 0) {
                try {
                    log.warn("Rate limit reached. Waiting for {} ms", waitTime);
                    Thread.sleep(waitTime);
                    // Recursively try again after waiting
                    acquire(); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for rate limit", e);
                }
            } else {
                 // Fallback if calculation is weird (e.g. clock skew), just proceed to avoid infinite loop
                 requestTimestamps.pollFirst();
                 requestTimestamps.addLast(now);
            }
        }
    }
}
