package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorRecordDto {
    private String id;
    private List<Float> embedding;
    private String text;
    private Map<String, Object> metadata;
    private Double score;
}