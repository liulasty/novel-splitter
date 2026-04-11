package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedTaskMessage implements Serializable {
    private String taskId;
    private String novelId;
    private String version;
    /**
     * 与 scenes 表分区一致；旧消息或仅单分区时可缺省，由 {@code EmbedWorker} 按 novelId+version 推断。
     */
    private Integer chunkSize;
    private Integer chunkOverlap;

    public EmbedTaskMessage(String taskId, String novelId, String version) {
        this.taskId = taskId;
        this.novelId = novelId;
        this.version = version;
    }

    public EmbedTaskMessage(String taskId, String novelId, String version, int chunkSize, int chunkOverlap) {
        this.taskId = taskId;
        this.novelId = novelId;
        this.version = version;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }
}
