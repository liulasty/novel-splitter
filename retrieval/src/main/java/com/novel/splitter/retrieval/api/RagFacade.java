package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.retrieval.dto.RagDebugResponse;
import com.novel.splitter.retrieval.dto.RagRequest;

/**
 * RAG 服务门面接口
 */
public interface RagFacade {

    /**
     * 提出问题并获取回答
     *
     * @param request 请求参数
     * @return 结构化回答
     */
    Answer ask(RagRequest request);

    /**
     * 提出问题并获取回答 (兼容旧接口)
     *
     * @param question 用户问题
     * @param topK     检索数量
     * @param novel    小说名称
     * @param version  版本
     * @return 结构化回答
     */
    Answer ask(String question, int topK, String novel, String version);

    /**
     * RAG 调试/预览 (不调用 LLM)
     *
     * @param request 请求参数
     * @return 调试信息
     */
    RagDebugResponse preview(RagRequest request);
}
