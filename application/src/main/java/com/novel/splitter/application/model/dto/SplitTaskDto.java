package com.novel.splitter.application.model.dto;

import com.novel.splitter.domain.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitTaskDto {
    private String taskId;
    private TaskType taskType;
    private String novelId;
    private String fileName;
    private int maxScenes;
    private String version;
    private String status; // String representation of TaskStatus
    private int progress;
    private String message;
    private long createdAt;
    private long updatedAt;
    private int totalScenes;
    private int completedScenes;
}