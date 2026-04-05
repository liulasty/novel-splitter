package com.novel.splitter.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "task_events", indexes = {
    @Index(name = "idx_task_events_task_id", columnList = "task_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JpaTaskEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private String taskId;

    @Column(name = "progress")
    private int progress;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private long createdAt;
}
