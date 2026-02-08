package com.novel.splitter.domain.model.llm.gemini;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class GeminiResponse {
    private List<GeminiCandidate> candidates;

    @Data
    @NoArgsConstructor
    public static class GeminiCandidate {
        private GeminiContent content;
        private String finishReason;
    }
}
