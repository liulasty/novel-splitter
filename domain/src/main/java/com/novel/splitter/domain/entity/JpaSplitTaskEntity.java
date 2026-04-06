package com.novel.splitter.domain.entity;

import com.novel.splitter.domain.task.SplitTask.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "split_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JpaSplitTaskEntity {

    @Id
    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    @Builder.Default
    private com.novel.splitter.domain.enums.TaskType taskType = com.novel.splitter.domain.enums.TaskType.SPLIT;

    @Column(name = "novel_id")
    private String novelId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "max_scenes")
    private int maxScenes;

    @Column(name = "version")
    private String version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    @Column(name = "progress")
    private int progress;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at")
    private long createdAt;

    @Column(name = "updated_at")
    private long updatedAt;

    @Column(name = "total_scenes")
    private int totalScenes;

    @Column(name = "completed_scenes")
    private int completedScenes;
}
