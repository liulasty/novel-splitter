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
public class GeminiContent {
    private String role;
    private List<GeminiPart> parts;
}
