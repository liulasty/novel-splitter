package com.novel.splitter.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cleanup_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JpaCleanupTaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "target_type", nullable = false)
    private String targetType; // e.g. "NOVEL", "VERSION"

    @Column(name = "version")
    private String version; // Optional, if targetType is VERSION

    @Column(name = "status", nullable = false)
    private String status; // e.g. "PENDING", "SUCCESS", "FAILED"

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}