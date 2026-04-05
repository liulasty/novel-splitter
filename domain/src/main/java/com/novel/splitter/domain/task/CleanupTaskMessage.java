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
}