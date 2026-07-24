package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerDto {
    private String answer;
    private List<CitationDto> citations;
    private Double confidence;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitationDto {
        private String chunkId;
        private String chapterPosition;
        private String reason;
        private String content;
        private Double score;
        private java.util.Map<String, Object> metadata;
    }
}