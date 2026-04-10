package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaCleanupTaskEntity;
import org.springframework.stereotype.Component;

@Component
public class CleanupTaskMapper {
    public CleanupTask toDomain(JpaCleanupTaskEntity entity) {
        if (entity == null) return null;
        return CleanupTask.builder()
                .id(entity.getId())
                .targetId(entity.getTargetId())
                .targetType(entity.getTargetType())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public JpaCleanupTaskEntity toEntity(CleanupTask domain) {
        if (domain == null) return null;
        JpaCleanupTaskEntity entity = new JpaCleanupTaskEntity();
        entity.setId(domain.getId());
        entity.setTargetId(domain.getTargetId());
        entity.setTargetType(domain.getTargetType());
        entity.setVersion(domain.getVersion());
        entity.setStatus(domain.getStatus());
        entity.setErrorMessage(domain.getErrorMessage());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}