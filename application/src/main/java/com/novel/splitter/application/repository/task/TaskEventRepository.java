package com.novel.splitter.application.repository.task;

import com.novel.splitter.domain.entity.JpaTaskEventEntity;
import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.repository.api.JpaTaskEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class TaskEventRepository {

    private final JpaTaskEventRepository jpaTaskEventRepository;

    public void save(TaskProgressEvent event) {
        JpaTaskEventEntity entity = toEntity(event);
        jpaTaskEventRepository.save(entity);
    }

    public List<TaskProgressEvent> findByTaskId(String taskId) {
        return jpaTaskEventRepository.findByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private JpaTaskEventEntity toEntity(TaskProgressEvent event) {
        return JpaTaskEventEntity.builder()
                .taskId(event.getTaskId())
                .progress(event.getProgress())
                .message(event.getMessage())
                .status(event.getStatus())
                .createdAt(event.getTimestamp())
                .build();
    }

    private TaskProgressEvent toDomain(JpaTaskEventEntity entity) {
        return new TaskProgressEvent(
                entity.getTaskId(),
                entity.getProgress(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }
}
