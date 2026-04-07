package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.repository.SplitTaskRepository;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaSplitTaskEntity;
import com.novel.splitter.infrastructure.persistence.mapper.SplitTaskMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaSplitTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SplitTaskRepositoryJpaImpl implements SplitTaskRepository {

    private final JpaSplitTaskRepository jpaSplitTaskRepository;
    private final SplitTaskMapper mapper = SplitTaskMapper.INSTANCE;

    @Override
    public void save(SplitTask task) {
        JpaSplitTaskEntity entity = mapper.toEntity(task);
        jpaSplitTaskRepository.save(entity);
    }

    @Override
    public Optional<SplitTask> findById(String taskId) {
        return jpaSplitTaskRepository.findById(taskId)
                .map(entity -> {
                    SplitTask task = mapper.toDomain(entity);
                    if (entity.getCompletedScenes() > 0) {
                        task.getCompletedScenes().set(entity.getCompletedScenes());
                    }
                    return task;
                });
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
        jpaSplitTaskRepository.deleteById(taskId);
    }
}