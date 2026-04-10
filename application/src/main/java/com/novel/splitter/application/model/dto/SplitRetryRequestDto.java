package com.novel.splitter.application.model.dto;

import lombok.Data;

/**
 * 手动重试 Split 阶段请求体。
 * 不依赖 fileName（不会重跑 Load）。
 */
@Data
public class SplitRetryRequestDto {
    private int maxScenes = 0;
    private String version;
    private boolean triggerEmbed = false;
}

