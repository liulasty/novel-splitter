package com.novel.splitter.llm.client.api;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.Prompt;

/**
 * LLM 客户端接口
 * <p>
 * 定义与大语言模型交互的标准契约。
 * 无论是真实的 OpenAI/Anthropic 客户端，还是本地的 Mock 实现，都应遵循此接口。
 * </p>
 */
public interface LlmClient {

    /**
     * 发送聊天请求并获取结构化回答
     *
     * @param prompt 包含系统指令、上下文证据和用户问题的完整 Prompt
     * @return 结构化的回答对象，包含内容、引用和置信度
     */
    Answer chat(Prompt prompt);
}
