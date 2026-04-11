package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Fine-grained embed work unit: one scene (DB row) per message.
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
    /** Must match {@code split_tasks.current_embed_run_id} for the task. */
    private String embedRunId;
    /** DB primary key of {@code scenes} row. */
    private Long scenePersistenceId;
}
