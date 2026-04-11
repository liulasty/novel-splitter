package com.novel.splitter.application.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IngestRequest {
    @NotBlank(message = "文件名不能为空")
    private String fileName;
    private int maxScenes = 0;
    private String version;
    private Integer chunkSize;
    private Integer chunkOverlap;
    /** 可选：章节标题行 Java 正则（整行匹配） */
    private String chapterTitleRegex;
}
