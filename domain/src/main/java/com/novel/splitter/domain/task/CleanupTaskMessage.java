package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupTaskMessage implements Serializable {
    private Long cleanupTaskId;
    private String targetId;
    private String targetType; // NOVEL 或 VERSION
    private String version;
    /**
     * 清理操作的推荐主标识。
     * 新消息建议设置 novelId，并保留 targetId 以兼容旧数据。
     */
    private String novelId;
    /**
     * 可选的旧名称（用于向后兼容与用户体验）。
     */
    private String novelName;

    /** 与场景分区一致；非空时仅清理该滑窗配置对应的向量 */
    private Integer chunkSize;
    private Integer chunkOverlap;

    /** 整书删除时捕获的该小说全部版本集合名；版本行已同步删除，供 CleanupWorker 按集合整删。 */
    private List<String> collectionNames;
}