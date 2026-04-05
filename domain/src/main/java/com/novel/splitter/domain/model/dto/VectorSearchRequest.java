package com.novel.splitter.domain.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class VectorSearchRequest {

    @NotBlank(message = "查询内容不能为空")
    private String query;

    @Min(value = 1, message = "topK 必须大于 0")
    private int topK = 5;

    private Map<String, Object> filter;
}
