package com.novel.splitter.application.service.task;

import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.application.repository.task.TaskRepository;
import com.novel.splitter.application.repository.task.TaskEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TaskService {
    
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskEventPublisher taskEventPublisher;

    @Autowired
    public TaskService(TaskRepository taskRepository, TaskEventRepository taskEventRepository, TaskEventPublisher taskEventPublisher) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.taskEventPublisher = taskEventPublisher;
    }

    @Transactional
    public SplitTask createTask(String taskId, String novelId, String fileName, int maxScenes, String version) {
        SplitTask task = new SplitTask(taskId, novelId, fileName, maxScenes, version);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public SplitTask getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    @Transactional(readOnly = true)
    public List<SplitTask> getAllTasks() {
        return taskRepository.findAll();
    }

    @Transactional
    public void updateTaskStatus(String taskId, SplitTask.TaskStatus status, int progress, String message) {
        SplitTask task = taskRepository.findById(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setProgress(progress);
            if (message != null) {
                task.setMessage(message);
            }
            taskRepository.save(task);
            
            // Publish lightweight event to MQ for SSE broadcast
            taskEventPublisher.publish(taskId, progress, message, status.name());
        }
    }

    @Transactional
    public void deleteTask(String taskId) {
        taskRepository.deleteById(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskProgressEvent> getTaskEvents(String taskId) {
        return taskEventRepository.findByTaskId(taskId);
    }
}
