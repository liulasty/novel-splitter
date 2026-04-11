package com.novel.splitter.domain.task;

public class SplitTaskMessage {
    private String taskId;
    private String novelId;
    private int maxScenes;
    private String version;
    private boolean triggerEmbed;
    /** true：忽略“已完整结构化”短路，强制清理并重新解析 */
    private boolean forceReload;
    /**
     * 服务重启后 Worker 可能需重建任务行：与 DB 中 task_type 一致（SPLIT / PIPELINE）。
     */
    private String taskTypeForRecovery;

    public SplitTaskMessage() {}

    public SplitTaskMessage(String taskId, String novelId, int maxScenes, String version) {
        this(taskId, novelId, maxScenes, version, false);
    }

    public SplitTaskMessage(String taskId, String novelId, int maxScenes, String version, boolean triggerEmbed) {
        this.taskId = taskId;
        this.novelId = novelId;
        this.maxScenes = maxScenes;
        this.version = version;
        this.triggerEmbed = triggerEmbed;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getNovelId() { return novelId; }
    public void setNovelId(String novelId) { this.novelId = novelId; }

    public int getMaxScenes() { return maxScenes; }
    public void setMaxScenes(int maxScenes) { this.maxScenes = maxScenes; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isTriggerEmbed() { return triggerEmbed; }
    public void setTriggerEmbed(boolean triggerEmbed) { this.triggerEmbed = triggerEmbed; }

    public boolean isForceReload() {
        return forceReload;
    }

    public void setForceReload(boolean forceReload) {
        this.forceReload = forceReload;
    }

    public String getTaskTypeForRecovery() {
        return taskTypeForRecovery;
    }

    public void setTaskTypeForRecovery(String taskTypeForRecovery) {
        this.taskTypeForRecovery = taskTypeForRecovery;
    }
}
