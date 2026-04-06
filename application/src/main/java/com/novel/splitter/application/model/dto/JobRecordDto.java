package com.novel.splitter.application.model.dto;

import com.novel.splitter.domain.task.SplitTask.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobRecordDto {
    private String id; // Use id for standard job identification
    private String taskId;
    private String novelId;
    private String fileName;
    private int maxScenes;
    private String version;
    private TaskStatus status;
    private int progress;
    private String message;
    private long createdAt;
    private long updatedAt;
    private int totalScenes;
    private int completedScenes;
}