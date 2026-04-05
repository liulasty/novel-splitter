package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.rag.RagService;
import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.domain.model.dto.ChatRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天对话控制器
 * 提供基于知识库的问答接口
 */
@Tag(name = "聊天对话管理", description = "提供基于知识库的问答接口")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final RagService ragService;

    /**
     * 发送聊天请求
     *
     * @param request 聊天请求参数，包含问题、知识库标识等
     * @return 包含回答内容的响应实体
     */
    @Operation(summary = "发送聊天请求", description = "根据用户输入的问题，从知识库中检索相关内容并生成回答")
    @PostMapping
    public Answer chat(@Valid @RequestBody ChatRequest request) {
        log.info("接收到聊天请求: {}", request);
        return ragService.ask(request.getQuestion(), request.getTopK(), request.getNovel(), request.getVersion());
    }
}
