package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressEventDto {
    private String id;
    private String taskId;
    private int progress;
    private String message;
    private String status;
    private long createdAt;
}