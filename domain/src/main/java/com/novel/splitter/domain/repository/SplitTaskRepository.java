package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.task.SplitTask;

import java.util.List;
import java.util.Optional;

public interface SplitTaskRepository {
    void save(SplitTask task);
    Optional<SplitTask> findById(String taskId);
    List<SplitTask> findAll();
    void deleteById(String taskId);
}