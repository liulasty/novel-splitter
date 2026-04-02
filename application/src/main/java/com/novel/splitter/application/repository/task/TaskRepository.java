package com.novel.splitter.application.repository.task;

import com.novel.splitter.application.model.task.SplitTask;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class TaskRepository {
    private final Map<String, SplitTask> taskStore = new ConcurrentHashMap<>();

    public SplitTask save(SplitTask task) {
        taskStore.put(task.getTaskId(), task);
        return task;
    }

    public SplitTask findById(String taskId) {
        return taskStore.get(taskId);
    }

    public List<SplitTask> findAll() {
        return new ArrayList<>(taskStore.values());
    }

    public void deleteById(String taskId) {
        taskStore.remove(taskId);
    }
}
