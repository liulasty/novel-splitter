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
     * 发送聊天请求
     * <p>
     * 委托给底层 LlmClient 处理（底层已包含重试机制），本层仅负责结果验证和修复。
     * </p>
     *
     * @param prompt 提示词
     * @return 结构化回答
     */
    public Answer chat(Prompt prompt) {
        try {
            Answer answer = llmClient.chat(prompt);
            
            // 格式验证和自动修复 (Format Validation & Auto-repair)
            return validateAndRepair(answer);

        } catch (Exception e) {
            log.error("LLM chat failed: {}", e.getMessage());
            throw e;
        }
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
