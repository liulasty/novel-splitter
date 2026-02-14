package com.novel.splitter.domain.model.dto;

import com.novel.splitter.domain.model.ContextBlock;
import com.novel.splitter.domain.model.Prompt;
import com.novel.splitter.domain.model.Scene;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RagDebugResponse {
    /** Step 1: 原始检索结果 (包含相似度分数) */
    private List<Scene> retrievedScenes;

    /** Step 2: 组装后的上下文块 (包含 Token 消耗、重排序结果) */
    private List<ContextBlock> contextBlocks;

    /** Step 3: 最终发送给 LLM 的 Prompt */
    private Prompt finalPrompt;

    /** 统计信息 (耗时、Token 预算等) */
    private Map<String, Object> stats;
}
