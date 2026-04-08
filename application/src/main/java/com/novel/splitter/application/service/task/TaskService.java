package com.novel.splitter.application.service.task;

import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.domain.repository.SplitTaskRepository;
import com.novel.splitter.domain.repository.TaskEventRepository;
import com.novel.splitter.application.model.dto.JobStatSummaryDto;
import com.novel.splitter.application.model.dto.JobRecordDto;
import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskService {
    
    private final SplitTaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskCachePort taskCachePort;

    @Autowired
    public TaskService(SplitTaskRepository taskRepository, TaskEventRepository taskEventRepository, TaskCachePort taskCachePort) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.taskCachePort = taskCachePort;
    }

    @Transactional
    public SplitTask createTask(String taskId, TaskType taskType, String novelId, String fileName, int maxScenes, String version) {
        SplitTask task = new SplitTask(taskId, taskType, novelId, fileName, maxScenes, version);
        taskRepository.save(task);
        
        taskCachePort.put(taskId, PollResponse.builder()
                .taskId(taskId)
                .status(task.getStatus().name())
                .progress(task.getProgress())
                .message(task.getMessage())
                .updatedAt(task.getUpdatedAt())
                .serverTime(System.currentTimeMillis())
                .build());
                
        return task;
    }

    @Transactional(readOnly = true)
    public SplitTask getTask(String taskId) {
        return taskRepository.findById(taskId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SplitTask> getAllTasks() {
        List<SplitTask> tasks = taskRepository.findAll();
        tasks.sort(Comparator.comparing(SplitTask::getCreatedAt).reversed());
        return tasks;
    }

    @Transactional
    public void updateTaskStatus(String taskId, SplitTask.TaskStatus status, int progress, String message) {
        SplitTask task = taskRepository.findById(taskId).orElse(null);
        if (task != null) {
            switch (status) {
                case PENDING:
                    task.setStatus(status);
                    task.setProgress(progress);
                    if (message != null) task.setMessage(message);
                    break;
                case PROCESSING:
                    if (task.getStatus() == SplitTask.TaskStatus.PENDING) {
                        task.startProcessing(message);
                    } else {
                        task.updateProgress(progress, message);
                    }
                    break;
                case SUCCESS:
                    task.markAsSuccess(message);
                    break;
                case FAILED:
                    task.markAsFailed(message);
                    break;
            }
            taskRepository.save(task);

            // Put task progress to cache for polling
            taskCachePort.put(taskId, PollResponse.builder()
                    .taskId(taskId)
                    .status(status.name())
                    .progress(progress)
                    .message(message)
                    .updatedAt(System.currentTimeMillis())
                    .serverTime(System.currentTimeMillis())
                    .build());
        }
    }

    @Transactional
    public void deleteTask(String taskId) {
        taskRepository.deleteById(taskId);
        taskCachePort.evict(taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskProgressEvent> getTaskEvents(String taskId, Long sinceTimestamp) {
        if (sinceTimestamp != null && sinceTimestamp > 0) {
            return taskEventRepository.findByTaskIdSince(taskId, sinceTimestamp);
        }
        return taskEventRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public JobStatSummaryDto getJobStats() {
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        List<SplitTask> allTasks = taskRepository.findAll();
        
        long running = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.PROCESSING).count();
        long waiting = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.PENDING).count();
        long completedToday = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.SUCCESS && t.getUpdatedAt() >= startOfDay).count();
        long failedToday = allTasks.stream().filter(t -> t.getStatus() == SplitTask.TaskStatus.FAILED && t.getUpdatedAt() >= startOfDay).count();
        
        return JobStatSummaryDto.builder()
                .running(running)
                .waiting(waiting)
                .completedToday(completedToday)
                .failedToday(failedToday)
                .build();
    }

    @Transactional(readOnly = true)
    public List<JobRecordDto> getAllJobs() {
        return taskRepository.findAll().stream()
                .map(this::mapToJobRecordDto)
                .sorted(Comparator.comparing(JobRecordDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    private JobRecordDto mapToJobRecordDto(SplitTask task) {
        return JobRecordDto.builder()
                .id(task.getTaskId())
                .taskId(task.getTaskId())
                .taskType(task.getTaskType())
                .novelId(task.getNovelId())
                .fileName(task.getFileName())
                .maxScenes(task.getMaxScenes())
                .version(task.getVersion())
                .status(task.getStatus())
                .progress(task.getProgress())
                .message(task.getMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .totalScenes(task.getTotalScenes())
                .completedScenes(task.getCompletedScenes().get())
                .build();
    }

    public void submitLoadTask(String novelId) {
        // Implementation provided by LoadWorker or RabbitMQ sending, kept interface compatible
    }

    public void submitSplitTask(String novelId) {
        // Implementation provided by LoadWorker or RabbitMQ sending, kept interface compatible
    }

    public void submitEmbedTask(String novelId) {
        // Implementation provided by LoadWorker or RabbitMQ sending, kept interface compatible
    }

    public void submitCleanupTask(String novelId) {
        // Implementation provided by LoadWorker or RabbitMQ sending, kept interface compatible
    }
}
