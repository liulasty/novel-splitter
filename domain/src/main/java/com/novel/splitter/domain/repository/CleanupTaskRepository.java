package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.task.CleanupTask;

import java.util.List;
import java.util.Optional;

public interface CleanupTaskRepository {
    CleanupTask save(CleanupTask task);
    List<CleanupTask> findAll();
    Optional<CleanupTask> findById(Long id);
}