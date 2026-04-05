package com.novel.splitter.domain.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DownloadAndIngestRequest {
    @NotBlank(message = "URL不能为空")
    private String url;
    
    @NotBlank(message = "小说名不能为空")
    private String name;
    
    private int maxScenes = 0;
    private String version;
}