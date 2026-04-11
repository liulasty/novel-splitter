package com.novel.splitter.pipeline.model;

/**
 * 场景滑窗在运行时的生效参数（与 {@link com.novel.splitter.pipeline.orchestrator.SplitNovelUseCase} 内计算一致）。
 */
public record ResolvedChunkingParams(int chunkSize, int chunkOverlap) {
}
