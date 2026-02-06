package com.novel.splitter.llm.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.llm.client.api.LlmClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * Ollama LLM Client implementation.
 * Connects to a local Ollama instance (default: http://localhost:11434).
 */
@Slf4j
public class OllamaLlmClient implements LlmClient {

    private final RestClient restClient;
    private final String modelName;
    private final ObjectMapper objectMapper;

    public OllamaLlmClient(
            @Value("${llm.ollama.url:http://localhost:11434}") String ollamaUrl,
            @Value("${llm.ollama.model:qwen2.5:7b}") String modelName,
            ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(ollamaUrl)
                .build();
        this.modelName = modelName;
        this.objectMapper = objectMapper;
        log.info("Initialized OllamaLlmClient with url: {}, model: {}", ollamaUrl, modelName);
    }

    @Override
    public Answer chat(Prompt prompt) {
        log.info("Sending request to Ollama model: {}", modelName);

        // 1. Construct messages
        List<Message> messages = new ArrayList<>();

        // System message: Instruction + Output Constraint
        String systemContent = prompt.getSystemInstruction();
        if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
            systemContent += "\n\nIMPORTANT OUTPUT FORMAT:\n" + prompt.getOutputConstraint();
        }
        // Enforce JSON schema in system prompt as well to be safe
        systemContent += "\n\nYou MUST respond with valid JSON matching the schema provided.";

        messages.add(Message.builder().role("system").content(systemContent).build());

        // User message: Context + Question
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
        userContent.append("Question: ").append(prompt.getUserQuestion());

        messages.add(Message.builder().role("user").content(userContent.toString()).build());

        // 2. Build Request
        OllamaRequest request = OllamaRequest.builder()
                .model(modelName)
                .messages(messages)
                .format("json")
                .stream(false)
                .options(Options.builder().temperature(0.7).build()) // Default temperature
                .build();

        try {
            // 3. Call API
            OllamaResponse response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(OllamaResponse.class);

            if (response == null || response.getMessage() == null) {
                throw new RuntimeException("Empty response from Ollama");
            }

            String content = response.getMessage().getContent();
            log.info("Ollama raw response: {}", content); // Changed to INFO for debugging

            // Clean up Markdown code blocks if present
            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            } else if (content.contains("```")) {
                content = content.replace("```", "");
            }
            content = content.trim();

            // 4. Parse Response to Answer object
            return objectMapper.readValue(content, Answer.class);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Ollama response JSON", e);
            throw new RuntimeException("Failed to parse LLM response", e);
        } catch (Exception e) {
            log.error("Error calling Ollama API", e);
            throw new RuntimeException("LLM communication failed: " + e.getMessage(), e);
        }
    }

    // --- Inner DTOs ---

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OllamaRequest {
        private String model;
        private List<Message> messages;
        private String format;
        private Boolean stream;
        private Options options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Options {
        private Double temperature;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OllamaResponse {
        private String model;
        private String created_at;
        private Message message;
        private String done_reason;
        private Boolean done;
        private Long total_duration;
        private Long load_duration;
        private Long prompt_eval_count;
        private Long prompt_eval_duration;
        private Long eval_count;
        private Long eval_duration;
    }
}
