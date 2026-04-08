package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.repository.SplitTaskRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaSplitTaskEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SplitTaskMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaSplitTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SplitTaskRepositoryJpaImpl implements SplitTaskRepository {

    private final JpaSplitTaskRepository jpaSplitTaskRepository;
    private final SplitTaskMapper mapper;

    @Override
    public void save(SplitTask task) {
        JpaSplitTaskEntity entity = mapper.toEntity(task);
        jpaSplitTaskRepository.save(Objects.requireNonNull(entity, "entity must not be null"));
    }

    @Override
    public Optional<SplitTask> findById(String taskId) {
        return jpaSplitTaskRepository.findById(Objects.requireNonNull(taskId, "taskId must not be null"))
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                });
    }

    @Override
    public List<SplitTask> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaSplitTaskRepository.findByTaskIdIn(ids).stream()
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SplitTask> findRecentByNovelId(String novelId, int limit) {
        if (novelId == null || novelId.isBlank()) {
            return List.of();
        }
        // Spring Data method fixed to top50; caller should pass limit <= 50.
        return jpaSplitTaskRepository.findTop50ByNovelIdOrderByUpdatedAtDesc(novelId).stream()
                .limit(Math.max(0, limit))
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<SplitTask> findAll() {
        return jpaSplitTaskRepository.findAll().stream()
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                })
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String taskId) {
        jpaSplitTaskRepository.deleteById(Objects.requireNonNull(taskId, "taskId must not be null"));
    }
}