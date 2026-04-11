package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaSplitTaskEntity;
import org.springframework.stereotype.Component;

@Component
public class SplitTaskMapper {
    public SplitTask toDomain(JpaSplitTaskEntity entity) {
        if (entity == null) return null;
        SplitTask task = new SplitTask();
        task.setTaskId(entity.getTaskId());
        task.setTaskType(entity.getTaskType());
        task.setNovelId(entity.getNovelId());
        task.setFileName(entity.getFileName());
        task.setMaxScenes(entity.getMaxScenes());
        task.setVersion(entity.getVersion());
        task.setStatus(entity.getStatus());
        task.setProgress(entity.getProgress());
        task.setMessage(entity.getMessage());
        task.setCreatedAt(entity.getCreatedAt());
        task.setUpdatedAt(entity.getUpdatedAt());
        task.setTotalScenes(entity.getTotalScenes());
        task.getCompletedScenes().set(entity.getCompletedScenes());
        task.setCurrentEmbedRunId(entity.getCurrentEmbedRunId());
        return task;
    }

    public JpaSplitTaskEntity toEntity(SplitTask domain) {
        if (domain == null) return null;
        JpaSplitTaskEntity entity = new JpaSplitTaskEntity();
        entity.setTaskId(domain.getTaskId());
        entity.setTaskType(domain.getTaskType());
        entity.setNovelId(domain.getNovelId());
        entity.setFileName(domain.getFileName());
        entity.setMaxScenes(domain.getMaxScenes());
        entity.setVersion(domain.getVersion());
        entity.setStatus(domain.getStatus());
        entity.setProgress(domain.getProgress());
        entity.setMessage(domain.getMessage());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setTotalScenes(domain.getTotalScenes());
        entity.setCompletedScenes(domain.getCompletedScenes() != null ? domain.getCompletedScenes().get() : 0);
        entity.setCurrentEmbedRunId(domain.getCurrentEmbedRunId());
        return entity;
    }
}