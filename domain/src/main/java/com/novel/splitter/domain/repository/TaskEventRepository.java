package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.task.TaskProgressEvent;

import java.util.Collection;
import java.util.List;

public interface TaskEventRepository {
    void save(TaskProgressEvent event);
    List<TaskProgressEvent> findByTaskId(String taskId);
    List<TaskProgressEvent> findByTaskIdSince(String taskId, long sinceTimestamp);

    /** 批量删除指定任务 id 的进度记录（为空时不执行任何操作）。 */
    int deleteByTaskIds(Collection<String> taskIds);
}