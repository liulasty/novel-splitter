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
     * 服务重启后 Worker 可能需重建任务行：与 DB 中 task_type 一致（CHAPTER_PARSE / SCENE_SPLIT / PIPELINE 等）。
     */
    private String taskTypeForRecovery;

    /**
     * 可选：场景滑窗切分的块大小（字/字符数）。未设置时由 Worker 使用全局配置。
     */
    private Integer chunkSize;
    /**
     * 可选：相邻块重叠字数。须小于 chunkSize；未设置时使用全局配置。
     */
    private Integer chunkOverlap;

    /**
     * 可选：章节标题行匹配的 Java 正则（整行）；空则使用默认规则。
     */
    private String chapterTitleRegex;

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

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(Integer chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public String getChapterTitleRegex() {
        return chapterTitleRegex;
    }

    public void setChapterTitleRegex(String chapterTitleRegex) {
        this.chapterTitleRegex = chapterTitleRegex;
    }
}
