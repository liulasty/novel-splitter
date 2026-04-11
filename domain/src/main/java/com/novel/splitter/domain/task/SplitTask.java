package com.novel.splitter.domain.task;

import com.novel.splitter.domain.enums.TaskType;

public class SplitTask {
    private String taskId;
    private TaskType taskType;
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
    private java.util.concurrent.atomic.AtomicInteger completedScenes = new java.util.concurrent.atomic.AtomicInteger(0);

    /** UUID for the current embed orchestration run; stale MQ messages must match this. */
    private String currentEmbedRunId;

    public enum TaskStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }

    public SplitTask() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.status = TaskStatus.PENDING;
        this.progress = 0;
    }

    public SplitTask(String taskId, TaskType taskType, String novelId, String fileName, int maxScenes, String version) {
        this();
        this.taskId = taskId;
        this.taskType = taskType != null ? taskType : TaskType.SCENE_SPLIT;
        this.novelId = novelId;
        this.fileName = fileName;
        this.maxScenes = maxScenes;
        this.version = version;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public TaskType getTaskType() { return taskType; }
    public void setTaskType(TaskType taskType) { this.taskType = taskType; }

    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getMaxScenes() { return maxScenes; }
    public void setMaxScenes(int maxScenes) { this.maxScenes = maxScenes; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { 
        this.status = status; 
        this.updatedAt = System.currentTimeMillis();
    }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { 
        this.progress = progress; 
        this.updatedAt = System.currentTimeMillis();
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { 
        this.message = message; 
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getTotalScenes() { return totalScenes; }
    public void setTotalScenes(int totalScenes) { this.totalScenes = totalScenes; }

    public java.util.concurrent.atomic.AtomicInteger getCompletedScenes() { return completedScenes; }

    public String getCurrentEmbedRunId() { return currentEmbedRunId; }
    public void setCurrentEmbedRunId(String currentEmbedRunId) { this.currentEmbedRunId = currentEmbedRunId; }

    // 领域行为
    public void startProcessing(String message) {
        if (this.status == TaskStatus.SUCCESS) {
            throw new IllegalStateException("Task is already completed successfully.");
        }
        this.status = TaskStatus.PROCESSING;
        this.message = message;
        this.updatedAt = System.currentTimeMillis();
    }

    public void updateProgress(int progress, String message) {
        this.progress = progress;
        if (message != null) {
            this.message = message;
        }
        this.updatedAt = System.currentTimeMillis();
    }

    public void markAsSuccess(String message) {
        this.status = TaskStatus.SUCCESS;
        this.progress = 100;
        this.message = message;
        this.updatedAt = System.currentTimeMillis();
    }

    public void markAsFailed(String message) {
        this.status = TaskStatus.FAILED;
        this.message = message;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isTerminal() {
        return this.status == TaskStatus.SUCCESS || this.status == TaskStatus.FAILED;
    }
}
