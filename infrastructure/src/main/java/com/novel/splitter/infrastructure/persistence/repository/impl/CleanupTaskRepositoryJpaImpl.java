package com.novel.splitter.infrastructure.persistence.repository.impl;

import com.novel.splitter.domain.repository.CleanupTaskRepository;
import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaCleanupTaskEntity;
import com.novel.splitter.infrastructure.persistence.mapper.CleanupTaskMapper;
import com.novel.splitter.infrastructure.persistence.repository.JpaCleanupTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CleanupTaskRepositoryJpaImpl implements CleanupTaskRepository {

    private final JpaCleanupTaskRepository jpaCleanupTaskRepository;
    private final CleanupTaskMapper mapper = CleanupTaskMapper.INSTANCE;

    @Override
    public void save(CleanupTask task) {
        JpaCleanupTaskEntity entity = mapper.toEntity(task);
        jpaCleanupTaskRepository.save(entity);
    }

    @Override
    public List<CleanupTask> findAll() {
        return jpaCleanupTaskRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public java.util.Optional<CleanupTask> findById(Long id) {
        return jpaCleanupTaskRepository.findById(id).map(mapper::toDomain);
    }
}