package com.novel.splitter.application.model.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class NovelPipelineRequestDto {
    @NotEmpty(message = "stages 不能为空")
    private List<String> stages;
    private String version;
    private int maxScenes = 0;
}
