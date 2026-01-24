package com.novel.splitter.assembler.api;

import com.novel.splitter.domain.model.Scene;
import java.util.List;

/**
 * 上下文组装器
 * <p>
 * 负责将 Retrieval 阶段返回的 SceneChunk 列表，整理为一段 Token 可控、结构稳定的 Context 文本。
 * </p>
 */
public interface ContextAssembler {

    /**
     * 组装 Context
     *
     * @param retrievedChunks 检索到的候选 Chunk 列表
     * @return 格式化后的纯文本 Context
     */
    String assemble(List<Scene> retrievedChunks);
}
