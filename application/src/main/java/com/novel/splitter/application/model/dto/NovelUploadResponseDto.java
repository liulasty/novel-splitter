package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说上传响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelUploadResponseDto {
    private String message;
    private String novelId;
    private String taskId;
}
