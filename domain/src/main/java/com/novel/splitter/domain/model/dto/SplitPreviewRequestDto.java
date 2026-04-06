package com.novel.splitter.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SplitPreviewRequestDto {
    @NotBlank(message = "Source text cannot be blank")
    private String sourceText;
    
    private String strategy;
    private Integer maxTokens;
    private Integer overlapTokens;
}
