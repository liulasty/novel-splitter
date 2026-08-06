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

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

/**
 * Ollama LLM 客户端实现。
 * 连接本地的 Ollama 实例（默认: http://localhost:11434）。
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
        log.info("OllamaLlmClient 初始化完成，URL: {}, 模型: {}", url, modelName);
    }

    @Override
    public Answer chat(Prompt prompt) {
        log.info("正在向 Ollama 模型发送请求: {}", modelName);

        // 1. 构建消息
        List<Message> messages = new ArrayList<>();

        // 系统消息：指令 + 输出约束
        String systemContent = prompt.getSystemInstruction();
        if (prompt.getOutputConstraint() != null && !prompt.getOutputConstraint().isEmpty()) {
            systemContent += "\n\nIMPORTANT OUTPUT FORMAT:\n" + prompt.getOutputConstraint();
        }
        // 同时在系统提示词中强制 JSON 结构，确保安全
        systemContent += "\n\nYou MUST respond with valid JSON matching the schema provided.";

        messages.add(Message.builder().role("system").content(systemContent).build());

        // 用户消息：上下文 + 问题
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

        messages.add(Message.builder().role("user").content(userContent.toString()).build());

        // 2. 构建请求
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
             optionsBuilder.temperature(0.7); // 未提供 options 时的默认值
        }

        OllamaRequest request = OllamaRequest.builder()
                .model(modelName)
                .messages(messages)
                .format(reqFormat)
                .stream(false)
                .options(optionsBuilder.build())
                .build();

        try {
            // 3. 调用 API
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
            log.info("Ollama 原始响应: {}", content); // 改为 INFO 便于调试

            // 清理 Markdown 代码块（如果存在）
            if (content.contains("```json")) {
                content = content.replace("```json", "").replace("```", "");
            } else if (content.contains("```")) {
                content = content.replace("```", "");
            }
            content = content.trim();

            // 处理 LLM 在 JSON 对象之后输出额外文本的情况
            // 使用 JsonParser 只读取第一个有效 JSON 对象并忽略其余内容
            int firstBrace = content.indexOf('{');
            if (firstBrace != -1) {
                content = content.substring(firstBrace);
            }

            // 4. 解析响应为 Answer 对象
            try (JsonParser parser = objectMapper.createParser(content)) {
                Answer answer = parser.readValueAs(Answer.class);

                // 校验并填充默认值
                if (answer.getAnswer() == null) {
                    log.warn("解析出的 answer 内容为空。原始响应可能不符合 Answer 结构。原始内容: {}", content);

                    // 兜底：尝试从其他字段恢复内容
                    try {
                        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(content);
                        if (rootNode.has("response")) {
                            com.fasterxml.jackson.databind.JsonNode respNode = rootNode.get("response");
                            if (respNode.isTextual()) {
                                answer.setAnswer(respNode.asText());
                            } else if (respNode.isObject()) {
                                // 例如 {"response": {"name": "...", "description": "..."}}
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
                        // 忽略兜底错误
                    }

                    if (answer.getAnswer() == null) {
                         if (content.contains("\"chunk_id\"")) {
                             throw new RuntimeException("LLM returned a Chunk object instead of Answer object. Prompt instructions ignored.");
                         }
                         throw new RuntimeException("LLM response missing 'answer' field.");
                    } else {
                        log.info("已从非标准 JSON 中恢复回答: {}", answer.getAnswer());
                    }
                }

                if (answer.getCitations() == null) {
                    answer.setCitations(new ArrayList<>());
                }
                if (answer.getConfidence() == null) {
                    answer.setConfidence(0.8); // 缺失时的默认置信度
                }

                return answer;
            }

        } catch (JsonProcessingException e) {
            log.error("解析 Ollama 响应 JSON 失败", e);
            throw new RuntimeException("Failed to parse LLM response", e);
        } catch (Exception e) {
            log.error("调用 Ollama API 出错", e);
            throw new RuntimeException("LLM communication failed: " + e.getMessage(), e);
        }
    }
}
