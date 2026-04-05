package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskProgressEvent {
    private String taskId;
    private int progress;
    private String message;
    private String status;
    private long timestamp;

    public TaskProgressEvent(String taskId, int progress, String message, String status) {
        this.taskId = taskId;
        this.progress = progress;
        this.message = message;
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }
}
