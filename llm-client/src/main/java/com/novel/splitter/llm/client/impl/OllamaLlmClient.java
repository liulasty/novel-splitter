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
import com.novel.splitter.llm.client.config.OllamaProperties;

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
    private final OllamaProperties properties;

    public OllamaLlmClient(
            OllamaProperties properties,
            ObjectMapper objectMapper) {
        String url = properties.getUrl() != null ? properties.getUrl() : "http://localhost:11434";
        this.modelName = properties.getModel() != null ? properties.getModel() : "qwen2.5:7b";
        
        this.restClient = RestClient.builder()
                .baseUrl(url)
                .build();
        this.properties = properties;
        this.objectMapper = objectMapper;
        log.info("Initialized OllamaLlmClient with url: {}, model: {}", url, modelName);
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
        userContent.append("User Question: ").append(prompt.getUserQuestion());
        userContent.append("\n\nPlease answer the question in the specified JSON format.");

        messages.add(Message.builder().role("user").content(userContent.toString()).build());

        // 2. Build Request
        Options.OptionsBuilder optionsBuilder = Options.builder();
        String reqFormat = "json";

        if (properties.getOptions() != null) {
            OllamaProperties.OptionsConfig cfg = properties.getOptions();
            if (cfg.getTemperature() != null) optionsBuilder.temperature(cfg.getTemperature());
            if (cfg.getNumCtx() != null) optionsBuilder.numCtx(cfg.getNumCtx());
            if (cfg.getNumThreads() != null) optionsBuilder.numThread(cfg.getNumThreads());
            if (cfg.getMaxTokens() != null) optionsBuilder.numPredict(cfg.getMaxTokens());
            if (cfg.getNumGpu() != null) optionsBuilder.numGpu(cfg.getNumGpu());
            if (cfg.getFormat() != null) reqFormat = cfg.getFormat();
        } else {
             optionsBuilder.temperature(0.7); // Default if no options provided
        }

        OllamaRequest request = OllamaRequest.builder()
                .model(modelName)
                .messages(messages)
                .format(reqFormat)
                .stream(false)
                .options(optionsBuilder.build())
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
                
                // Validate and fill defaults
                if (answer.getAnswer() == null) {
                    log.warn("Parsed answer content is null. Raw response might not match Answer schema. Raw: {}", content);
                    
                    // Fallback: try to recover content from alternative fields
                    try {
                        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(content);
                        if (rootNode.has("response")) {
                            com.fasterxml.jackson.databind.JsonNode respNode = rootNode.get("response");
                            if (respNode.isTextual()) {
                                answer.setAnswer(respNode.asText());
                            } else if (respNode.isObject()) {
                                // e.g. {"response": {"name": "...", "description": "..."}}
                                if (respNode.has("description")) {
                                    answer.setAnswer(respNode.get("description").asText());
                                } else {
                                    answer.setAnswer(respNode.toString());
                                }
                            }
                        } else if (rootNode.has("content")) {
                             answer.setAnswer(rootNode.get("content").asText());
                        } else if (rootNode.has("message")) {
                             answer.setAnswer(rootNode.get("message").asText());
                        }
                    } catch (Exception ignored) {
                        // ignore fallback errors
                    }

                    if (answer.getAnswer() == null) {
                         if (content.contains("\"chunk_id\"")) {
                             throw new RuntimeException("LLM returned a Chunk object instead of Answer object. Prompt instructions ignored.");
                         }
                         throw new RuntimeException("LLM response missing 'answer' field.");
                    } else {
                        log.info("Recovered answer from non-standard JSON: {}", answer.getAnswer());
                    }
                }

                if (answer.getCitations() == null) {
                    answer.setCitations(new ArrayList<>());
                }
                if (answer.getConfidence() == null) {
                    answer.setConfidence(0.8); // Default confidence if missing
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
