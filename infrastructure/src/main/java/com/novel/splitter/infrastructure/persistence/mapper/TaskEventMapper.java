package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.infrastructure.persistence.entity.JpaTaskEventEntity;
import org.springframework.stereotype.Component;

@Component
public class TaskEventMapper {
    public TaskProgressEvent toDomain(JpaTaskEventEntity entity) {
        if (entity == null) return null;
        return new TaskProgressEvent(
                entity.getTaskId(),
                entity.getProgress(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    public JpaTaskEventEntity toEntity(TaskProgressEvent domain) {
        if (domain == null) return null;
        JpaTaskEventEntity entity = new JpaTaskEventEntity();
        // entity.id 由数据库自动生成
        entity.setTaskId(domain.getTaskId());
        entity.setProgress(domain.getProgress());
        entity.setMessage(domain.getMessage());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getTimestamp());
        return entity;
    }
}