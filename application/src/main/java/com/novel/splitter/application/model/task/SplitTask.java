package com.novel.splitter.application.model.task;

public class SplitTask {
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

    public SplitTask(String taskId, String novelId, String fileName, int maxScenes, String version) {
        this();
        this.taskId = taskId;
        this.novelId = novelId;
        this.fileName = fileName;
        this.maxScenes = maxScenes;
        this.version = version;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

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
}
