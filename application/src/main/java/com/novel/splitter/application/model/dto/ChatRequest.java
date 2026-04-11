package com.novel.splitter.application.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {
    @NotBlank(message = "问题不能为空")
    private String question;

    @Min(value = 1, message = "topK 必须大于 0")
    private int topK = 3;
    private String novelId;
    private String version;
    /** 可选；同一 version 下多数据集时必须提供 */
    private Integer chunkSize;
    private Integer chunkOverlap;
}
