package com.novel.splitter.application.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 手动重试 Split 阶段请求体。
 * 不依赖 fileName（不会重跑 Load）。
 * <p>
 * version 语义同 {@link SceneSplitRequestDto}：决定 (novelId, version) 分区，重试前仍会清理该版本旧数据再写入新的一批 Scene。
 */
@Data
public class SplitRetryRequestDto {
    private int maxScenes = 0;
    @Schema(description = "与首次场景切分时一致的 version；换 chunk 参数若需保留旧切片请改用新 version")
    private String version;
    private boolean triggerEmbed = false;
    private Integer chunkSize;
    private Integer chunkOverlap;
}

