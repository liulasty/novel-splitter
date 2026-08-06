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
    /**
     * 小说展示标题（面向用户，来自数据库）。
     * 可选，为向后兼容保留。
     */
    private String novelTitle;
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