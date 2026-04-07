package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.task.CleanupTask;

import java.util.List;

public interface CleanupTaskRepository {
    void save(CleanupTask task);
    List<CleanupTask> findAll();
}