package com.novel.splitter.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {
    private Long id;
    /** 配置键，dotted-path 格式，如 "splitter.ingestion.chunk-size" */
    private String configKey;
    /** 配置值 */
    private String configValue;
    /** 分类，如 "embedding", "llm", "chroma", "splitter" */
    private String category;
    /** 说明 */
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
