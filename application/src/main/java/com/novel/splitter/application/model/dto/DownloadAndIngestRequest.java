package com.novel.splitter.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class DownloadAndIngestRequest {
    @NotBlank(message = "URL不能为空")
    private String url;
    
    @NotBlank(message = "小说名不能为空")
    private String name;
    
    private int maxScenes = 0;
    private String version;
    /**
     * 可选处理阶段，默认执行全流程 [SPLIT, EMBED]
     */
    private List<String> stages;
}