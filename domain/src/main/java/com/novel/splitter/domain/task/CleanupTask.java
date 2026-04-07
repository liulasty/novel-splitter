package com.novel.splitter.domain.task;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CleanupTask {
    private Long id;
    private String targetId;
    private String targetType; // e.g. "NOVEL", "VERSION"
    private String version;    // Optional, if targetType is VERSION
    private String status;     // e.g. "PENDING", "SUCCESS", "FAILED"
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}