package com.novel.splitter.llm.client.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.llm.ollama.Message;
import com.novel.splitter.domain.model.llm.ollama.OllamaRequest;
import com.novel.splitter.domain.model.llm.ollama.OllamaResponse;
import com.novel.splitter.domain.model.llm.ollama.Options;
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

            // Handle cases where LLM outputs extra text after the JSON object
            // We use JsonParser to read only the first valid JSON object and ignore the rest
            int firstBrace = content.indexOf('{');
            if (firstBrace != -1) {
                content = content.substring(firstBrace);
            }

            // 4. Parse Response to Answer object
            try (JsonParser parser = objectMapper.createParser(content)) {
                Answer answer = parser.readValueAs(Answer.class);
                
                // Validate parsed answer
                if (answer.getAnswer() == null) {
                    log.warn("Parsed answer content is null. Raw response might not match Answer schema. Raw: {}", content);
                    // Fallback: if the raw content looks like a simple string or the model failed to format, 
                    // we might want to wrap the whole content as the answer?
                    // For now, let's trust the schema enforcement in prompt, but maybe throw exception if strict.
                    // Or if it parsed valid JSON but wrong fields (like chunk_id), we should error out.
                    
                    // If content has "chunk_id", it means model returned a chunk.
                    if (content.contains("\"chunk_id\"")) {
                         throw new RuntimeException("LLM returned a Chunk object instead of Answer object. Prompt instructions ignored.");
                    }
                }
                return answer;
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Ollama response JSON", e);
            throw new RuntimeException("Failed to parse LLM response", e);
        } catch (Exception e) {
            log.error("Error calling Ollama API", e);
            throw new RuntimeException("LLM communication failed: " + e.getMessage(), e);
        }
    }
}
