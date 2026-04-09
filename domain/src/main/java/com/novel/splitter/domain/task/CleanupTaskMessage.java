package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupTaskMessage implements Serializable {
    private Long cleanupTaskId;
    private String targetId;
    private String targetType; // NOVEL or VERSION
    private String version;
    /**
     * Recommended primary identifier for cleanup.
     * For new messages, prefer setting novelId and leaving targetId for backward compatibility.
     */
    private String novelId;
    /**
     * Optional legacy name (used for backward compatibility and UX).
     */
    private String novelName;
}