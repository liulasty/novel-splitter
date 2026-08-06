package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 细粒度的向量化工作单元：每条消息对应一个场景（DB 行）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbedSceneTaskMessage implements Serializable {
    private String taskId;
    private String novelId;
    private String version;
    private int chunkSize;
    private int chunkOverlap;
    /** 必须与该任务的 {@code split_tasks.current_embed_run_id} 一致。 */
    private String embedRunId;
    /** {@code scenes} 行的数据库主键。 */
    private Long scenePersistenceId;
}
