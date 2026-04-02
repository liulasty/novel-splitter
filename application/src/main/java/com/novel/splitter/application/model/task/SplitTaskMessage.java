package com.novel.splitter.application.model.task;

public class SplitTaskMessage {
    private String taskId;
    private String novelId;
    private String filePath;

    public SplitTaskMessage() {}

    public SplitTaskMessage(String taskId, String novelId, String filePath) {
        this.taskId = taskId;
        this.novelId = novelId;
        this.filePath = filePath;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
}
