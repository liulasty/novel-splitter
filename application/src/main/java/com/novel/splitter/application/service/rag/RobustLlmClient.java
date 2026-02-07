package com.novel.splitter.application.service.rag;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.llm.client.api.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 增强的 LLM 客户端
 * <p>
 * 包装现有 LlmClient，提供重试、格式验证和自动修复功能。
 * </p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RobustLlmClient {

    private final LlmClient llmClient;

    /**
     * 发送聊天请求，带重试机制
     *
     * @param prompt     提示词
     * @param maxRetries 最大重试次数
     * @return 结构化回答
     */
    public Answer chat(Prompt prompt, int maxRetries) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                if (attempt > 0) {
                    log.info("Retrying LLM request (attempt {}/{})", attempt + 1, maxRetries + 1);
                }
                
                Answer answer = llmClient.chat(prompt);
                
                // 格式验证和自动修复 (Format Validation & Auto-repair)
                return validateAndRepair(answer);

            } catch (Exception e) {
                log.warn("LLM chat failed (attempt {}/{}): {}", attempt + 1, maxRetries + 1, e.getMessage());
                lastException = e;
            }
            attempt++;
        }
        
        log.error("All retries failed for LLM chat.");
        throw new RuntimeException("LLM service failed after " + maxRetries + " retries", lastException);
    }

    private Answer validateAndRepair(Answer answer) {
        if (answer == null) {
            throw new IllegalStateException("LLM returned null answer");
        }
        
        boolean modified = false;
        
        if (answer.getCitations() == null) {
            answer.setCitations(Collections.emptyList());
            modified = true;
        }
        
        if (answer.getConfidence() == null) {
            answer.setConfidence(0.0);
            modified = true;
        }

        if (answer.getAnswer() == null) {
            answer.setAnswer("（未生成回答）");
            modified = true;
        }

        if (modified) {
            log.debug("Auto-repaired LLM answer format: {}", answer);
        }
        
        return answer;
    }
}
