package com.novel.splitter.application.model.task;

public class SplitTaskMessage {
    private String taskId;
    private String novelId;
    private String filePath;
    private int maxScenes;
    private String version;

    public SplitTaskMessage() {}

    public SplitTaskMessage(String taskId, String novelId, String filePath, int maxScenes, String version) {
        this.taskId = taskId;
        this.novelId = novelId;
        this.filePath = filePath;
        this.maxScenes = maxScenes;
        this.version = version;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getMaxScenes() { return maxScenes; }
    public void setMaxScenes(int maxScenes) { this.maxScenes = maxScenes; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
}
