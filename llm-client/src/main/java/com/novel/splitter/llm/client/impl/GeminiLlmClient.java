package com.novel.splitter.llm.client.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.google.genai.Client;
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
 * Gemini API Client Implementation.
 * Uses Google AI Gemini API (google-genai SDK).
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
        
        // Initialize Client using Builder
        Client.Builder builder = Client.builder()
                .apiKey(properties.getApiKey());

        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isEmpty()) {
            builder.httpOptions(HttpOptions.builder().baseUrl(properties.getBaseUrl()).build());
        }

        this.client = builder.build();

        this.rateLimiter = new RateLimiter(
                properties.getRateLimit().getMaxRequests(),
                properties.getRateLimit().getDurationSeconds()
        );
        log.info("Initialized GeminiLlmClient with model: {}", properties.getModel());
    }

    @Override
    public Answer chat(Prompt prompt) {
        // Rate Limit Check
        rateLimiter.acquire();

        log.info("Sending request to Gemini model: {}", properties.getModel());

        try {
            // 1. Construct User Content
            StringBuilder userContentBuilder = new StringBuilder();
            if (prompt.getContextBlocks() != null && !prompt.getContextBlocks().isEmpty()) {
                userContentBuilder.append("Context Information:\n");
                for (ContextBlock block : prompt.getContextBlocks()) {
                    userContentBuilder.append("---\n");
                    userContentBuilder.append("Chunk ID: ").append(block.getChunkId()).append("\n");
                    if (block.getSceneMetadata() != null) {
                        userContentBuilder.append("Source: ").append(block.getSceneMetadata().getChapterTitle()).append("\n");
                    }
                    userContentBuilder.append("Content: ").append(block.getContent()).append("\n");
                    userContentBuilder.append("---\n");
                }
                userContentBuilder.append("\n");
            }
            userContentBuilder.append("User Question: ").append(prompt.getUserQuestion());
            userContentBuilder.append("\n\nPlease answer the question in the specified JSON format.");

            // 2. Construct System Instruction
            String systemText = prompt.getSystemInstruction();
            if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
                systemText += "\n\nIMPORTANT OUTPUT FORMAT:\n" + prompt.getOutputConstraint();
            }
            systemText += "\n\nYou MUST respond with valid JSON matching the schema provided.";
            
            Content systemInstructionContent = Content.fromParts(Part.fromText(systemText));
            
            // 3. Configure Generation
            GenerateContentConfig config = GenerateContentConfig.builder()
                    .responseMimeType("application/json")
                    .temperature(0.1f)
                    .maxOutputTokens(2048)
                    .systemInstruction(systemInstructionContent)
                    .build();

            // 4. Call API
            GenerateContentResponse response = client.models.generateContent(
                    properties.getModel(),
                    Content.fromParts(Part.fromText(userContentBuilder.toString())),
                    config
            );

            String content = response.text();
            log.info("Gemini raw response: {}", content);

            if (content == null || content.isEmpty()) {
                throw new RuntimeException("Empty response from Gemini");
            }

            // Clean up Markdown code blocks if present
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

            // 5. Parse Response
            try (JsonParser parser = objectMapper.createParser(content)) {
                return parser.readValueAs(Answer.class);
            }

        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Gemini API call failed: " + e.getMessage(), e);
        }
    }

    // Simple Token Bucket Rate Limiter (Duplicated from DeepSeekLlmClient for independence)
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
                 requestTimestamps.pollFirst();
                 requestTimestamps.addLast(now);
            }
        }
    }
}
