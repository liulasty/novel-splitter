package com.novel.splitter.retrieval.api;

import com.novel.splitter.domain.model.Answer;
import com.novel.splitter.retrieval.dto.RagDebugResponse;
import com.novel.splitter.retrieval.dto.RagRequest;

/**
 * RAG（检索增强生成）服务门面接口
 * <p>
 * 作为整个小说 RAG 问答系统对外的统一交互入口，
 * 封装了意图识别、向量检索、上下文组装以及大模型生成的复杂流程。
 * </p>
 */
public interface RagFacade {

    /**
     * 提出问题并获取基于小说内容的结构化回答。
     * 
     * // 此方法为新版接口，使用封装好的请求对象，便于后续扩展更多参数
     *
     * @param request 封装了问题及相关配置的请求参数对象
     * @return 包含最终生成结果及相关溯源信息的结构化回答对象
     */
    Answer ask(RagRequest request);

    /**
     * 提出问题并获取基于小说内容的结构化回答（兼容旧接口）。
     * 
     * // 允许通过散列参数直接调用问答流程，方便旧版客户端迁移
     *
     * @param question 用户输入的自然语言问题
     * @param topK     需要检索的最相关上下文片段数量
     * @param novel    指定查询的小说名称，用于限定检索范围
     * @param version  数据版本号（如：v1，用于区分不同解析版本的数据）
     * @return 包含最终生成结果及相关溯源信息的结构化回答对象
     */
    Answer ask(String question, int topK, String novel, String version);

    /**
     * RAG 流程的调试与预览模式（仅执行检索和提示词组装，不调用大语言模型）。
     * 
     * // 适用于开发人员或后台系统验证检索质量及上下文构建逻辑
     *
     * @param request 封装了问题及相关配置的请求参数对象
     * @return 包含原始检索结果、上下文块、最终组装的提示词以及统计信息的调试响应对象
     */
    RagDebugResponse preview(RagRequest request);
}
