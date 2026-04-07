package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.task.TaskProgressEvent;

import java.util.List;

public interface TaskEventRepository {
    void save(TaskProgressEvent event);
    List<TaskProgressEvent> findByTaskId(String taskId);
    List<TaskProgressEvent> findByTaskIdSince(String taskId, long sinceTimestamp);
}