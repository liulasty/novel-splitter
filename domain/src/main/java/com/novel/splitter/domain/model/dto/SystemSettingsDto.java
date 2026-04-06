package com.novel.splitter.domain.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * DTO for system settings matching frontend requirements.
 */
@Data
@Schema(description = "系统设置DTO")
public class SystemSettingsDto {
    
    @Schema(description = "Embedding 配置")
    private Map<String, Object> embedding;
    
    @Schema(description = "LLM 配置")
    private Map<String, Object> llm;
    
    @Schema(description = "Chroma 配置")
    private Map<String, Object> chroma;
    
    @Schema(description = "切分策略配置")
    private Map<String, Object> splitStrategy;
}
