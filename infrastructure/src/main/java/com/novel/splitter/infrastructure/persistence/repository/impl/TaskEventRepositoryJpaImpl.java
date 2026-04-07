package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.repository.TaskEventRepository;
import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.infrastructure.persistence.entity.JpaTaskEventEntity;
import com.novel.splitter.infrastructure.persistence.mapper.TaskEventMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaTaskEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskEventRepositoryJpaImpl implements TaskEventRepository {

    private final JpaTaskEventRepository jpaTaskEventRepository;
    private final TaskEventMapper mapper = TaskEventMapper.INSTANCE;

    @Override
    public void save(TaskProgressEvent event) {
        JpaTaskEventEntity entity = mapper.toEntity(event);
        jpaTaskEventRepository.save(entity);
    }

    @Override
    public List<TaskProgressEvent> findByTaskId(String taskId) {
        return jpaTaskEventRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskProgressEvent> findByTaskIdSince(String taskId, long sinceTimestamp) {
        return jpaTaskEventRepository.findByTaskIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(taskId, sinceTimestamp).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}