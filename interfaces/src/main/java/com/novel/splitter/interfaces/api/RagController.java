package com.novel.splitter.interfaces.api;

import com.novel.splitter.retrieval.api.RagFacade;
import com.novel.splitter.application.model.dto.AnswerDto;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.retrieval.dto.RagDebugResponse;
import com.novel.splitter.retrieval.dto.RagRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG（检索增强生成）问答控制器
 * 提供标准问答和调试预览接口
 */
@Tag(name = "RAG问答管理", description = "提供RAG标准问答和调试预览接口")
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagFacade ragService;

    /**
     * RAG 问答接口
     *
     * @param request RAG 请求参数，包含问题、TopK、小说及版本
     * @return 大语言模型生成的回答
     */
    @Operation(summary = "RAG问答请求", description = "通过检索增强生成机制，基于小说知识库回答用户问题")
    @PostMapping
    public AnswerDto ask(@Valid @RequestBody RagRequest request) {
        return DtoMapper.INSTANCE.toAnswerDto(ragService.ask(request));
    }

    /**
     * RAG 调试预览接口
     *
     * @param request RAG 请求参数
     * @return 包含检索上下文的调试响应，用于查看大模型的输入上下文
     */
    @Operation(summary = "RAG调试预览", description = "获取检索到的上下文信息，不调用大模型生成最终回答，用于调试")
    @PostMapping("/debug")
    public RagDebugResponse debug(@Valid @RequestBody RagRequest request) {
        return ragService.preview(request);
    }
}
