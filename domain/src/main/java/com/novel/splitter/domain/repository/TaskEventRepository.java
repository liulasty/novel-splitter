package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.task.TaskProgressEvent;

import java.util.Collection;
import java.util.List;

public interface TaskEventRepository {
    void save(TaskProgressEvent event);
    List<TaskProgressEvent> findByTaskId(String taskId);
    List<TaskProgressEvent> findByTaskIdSince(String taskId, long sinceTimestamp);

    /** Bulk-delete progress rows for the given task ids (no-op if empty). */
    int deleteByTaskIds(Collection<String> taskIds);
}