package com.novel.splitter.domain.model.llm.openai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private Boolean stream;
    private Double temperature;
    private Integer max_tokens;
    private ResponseFormat response_format;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseFormat {
        private String type;
    }
}
