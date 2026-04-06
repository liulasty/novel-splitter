package com.novel.splitter.application.repository.task;

import com.novel.splitter.domain.entity.JpaSplitTaskEntity;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.repository.api.JpaSplitTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TaskRepository {
    
    private final JpaSplitTaskRepository jpaSplitTaskRepository;

    public SplitTask save(SplitTask task) {
        JpaSplitTaskEntity entity = toEntity(task);
        jpaSplitTaskRepository.save(entity);
        return task;
    }

    public SplitTask findById(String taskId) {
        return jpaSplitTaskRepository.findById(taskId)
                .map(this::toDomain)
                .orElse(null);
    }

    public List<SplitTask> findAll() {
        return jpaSplitTaskRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    public void deleteById(String taskId) {
        jpaSplitTaskRepository.deleteById(taskId);
    }

    private JpaSplitTaskEntity toEntity(SplitTask task) {
        return JpaSplitTaskEntity.builder()
                .taskId(task.getTaskId())
                .taskType(task.getTaskType())
                .novelId(task.getNovelId())
                .fileName(task.getFileName())
                .maxScenes(task.getMaxScenes())
                .version(task.getVersion())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .totalScenes(task.getTotalScenes())
                .completedScenes(task.getCompletedScenes().get())
                .build();
    }

    private SplitTask toDomain(JpaSplitTaskEntity entity) {
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
        return task;
    }
}
