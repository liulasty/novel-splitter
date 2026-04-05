package com.novel.splitter.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngestRequest {
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    private int maxScenes = 0;
    private String version;
}
