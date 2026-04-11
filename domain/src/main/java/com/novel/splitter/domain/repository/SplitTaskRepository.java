package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskFilter;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SplitTaskRepository {
    void save(SplitTask task);
    Optional<SplitTask> findById(String taskId);
    List<SplitTask> findByIds(List<String> ids);
    List<SplitTask> findRecentByNovelId(String novelId, int limit);
    List<SplitTask> findAll();
    PagedResult<SplitTask> findFiltered(SplitTaskFilter filter);
    void deleteById(String taskId);

    List<String> findTaskIdsByNovelIdAndStatuses(String novelId, List<SplitTask.TaskStatus> statuses);

    /**
     * Terminal tasks whose {@link SplitTask#getVersion()} equals {@code version}.
     * Chunk profile is not stored on {@link SplitTask}; all terminal tasks for this version string match.
     */
    List<String> findTaskIdsByNovelIdAndVersionAndStatuses(String novelId, String version, List<SplitTask.TaskStatus> statuses);

    void deleteAllByIds(Collection<String> taskIds);
}