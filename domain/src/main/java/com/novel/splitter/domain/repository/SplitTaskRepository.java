package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.paging.PagedResult;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.SplitTaskFilter;

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
}