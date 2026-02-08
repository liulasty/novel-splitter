package com.novel.splitter.domain.model.llm.gemini;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRequest {
    private GeminiContent system_instruction;
    private List<GeminiContent> contents;
    private GenerationConfig generationConfig;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationConfig {
        private String responseMimeType;
        private Double temperature;
        private Integer maxOutputTokens;
    }
}
