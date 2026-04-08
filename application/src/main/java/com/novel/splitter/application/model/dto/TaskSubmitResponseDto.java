package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 异步任务提交响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmitResponseDto {
    private String message;
    private String taskId;
}
