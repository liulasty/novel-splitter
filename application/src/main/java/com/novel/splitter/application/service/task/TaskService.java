package com.novel.splitter.application.service.task;

import com.novel.splitter.application.model.task.SplitTask;
import com.novel.splitter.application.repository.task.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {
    
    private final TaskRepository taskRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public SplitTask createTask(String taskId, String novelId, String fileName) {
        SplitTask task = new SplitTask(taskId, novelId, fileName);
        return taskRepository.save(task);
    }

    public SplitTask getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    public List<SplitTask> getAllTasks() {
        return taskRepository.findAll();
    }

    public void updateTaskStatus(String taskId, SplitTask.TaskStatus status, int progress, String message) {
        SplitTask task = taskRepository.findById(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setProgress(progress);
            if (message != null) {
                task.setMessage(message);
            }
            taskRepository.save(task);
        }
    }

    public void deleteTask(String taskId) {
        taskRepository.deleteById(taskId);
    }
}
