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
    private String targetType; // 例如："NOVEL"、"VERSION"
    private String version;    // 可选，targetType 为 VERSION 时使用
    private String status;     // 例如："PENDING"、"SUCCESS"、"FAILED"
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}