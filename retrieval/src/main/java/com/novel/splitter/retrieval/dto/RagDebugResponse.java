package com.novel.splitter.retrieval.dto;

import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * RAG 调试响应数据传输对象 (DTO)
 * <p>
 * 用于封装不调用大模型时的中间调试信息，帮助开发者分析和优化
 * 检索效果、上下文组装逻辑以及 Token 消耗等。
 * </p>
 */
@Data
@Builder
public class RagDebugResponse {
    /** 
     * Step 1: 原始检索结果 
     * // 包含从向量数据库中初步召回的小说场景片段，通常带有相似度分数 
     */
    private List<Scene> retrievedScenes;

    /** 
     * Step 2: 组装后的上下文块 
     * // 经过重排序、去重以及内容截断后的上下文块列表，包含 Token 消耗估算信息 
     */
    private List<ContextBlock> contextBlocks;

    /** 
     * Step 3: 最终发送给 LLM 的 Prompt 
     * // 结合了系统指令、检索到的上下文内容以及用户问题的完整提示词对象 
     */
    private Prompt finalPrompt;

    /** 
     * 统计信息 
     * // 记录各阶段的执行耗时、Token 预算使用情况及其他性能指标 
     */
    private Map<String, Object> stats;
}
