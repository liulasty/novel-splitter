package com.novel.splitter.application.service.task;

import com.novel.splitter.domain.enums.TaskType;
import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.application.repository.task.TaskRepository;
import com.novel.splitter.application.repository.task.TaskEventRepository;
import com.novel.splitter.application.model.dto.JobStatSummaryDto;
import com.novel.splitter.application.model.dto.JobRecordDto;
import com.novel.splitter.repository.api.JpaSplitTaskRepository;
import com.novel.splitter.domain.entity.JpaSplitTaskEntity;
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
    
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskEventPublisher taskEventPublisher;
    private final JpaSplitTaskRepository jpaSplitTaskRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository, TaskEventRepository taskEventRepository, TaskEventPublisher taskEventPublisher, JpaSplitTaskRepository jpaSplitTaskRepository) {
        this.taskRepository = taskRepository;
        this.taskEventRepository = taskEventRepository;
        this.taskEventPublisher = taskEventPublisher;
        this.jpaSplitTaskRepository = jpaSplitTaskRepository;
    }

    @Transactional
    public SplitTask createTask(String taskId, TaskType taskType, String novelId, String fileName, int maxScenes, String version) {
        SplitTask task = new SplitTask(taskId, taskType, novelId, fileName, maxScenes, version);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public SplitTask getTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    @Transactional(readOnly = true)
    public List<SplitTask> getAllTasks() {
        List<SplitTask> tasks = taskRepository.findAll();
        tasks.sort(Comparator.comparing(SplitTask::getCreatedAt).reversed());
        return tasks;
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
    public List<TaskProgressEvent> getTaskEvents(String taskId, Long sinceTimestamp) {
        if (sinceTimestamp != null && sinceTimestamp > 0) {
            return taskEventRepository.findByTaskIdSince(taskId, sinceTimestamp);
        }
        return taskEventRepository.findByTaskId(taskId);
    }

    @Transactional(readOnly = true)
    public JobStatSummaryDto getJobStats() {
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        
        long running = jpaSplitTaskRepository.countByStatus(SplitTask.TaskStatus.PROCESSING);
        long waiting = jpaSplitTaskRepository.countByStatus(SplitTask.TaskStatus.PENDING);
        long completedToday = jpaSplitTaskRepository.countByStatusAndUpdatedAtGreaterThanEqual(SplitTask.TaskStatus.SUCCESS, startOfDay);
        long failedToday = jpaSplitTaskRepository.countByStatusAndUpdatedAtGreaterThanEqual(SplitTask.TaskStatus.FAILED, startOfDay);
        
        return JobStatSummaryDto.builder()
                .running(running)
                .waiting(waiting)
                .completedToday(completedToday)
                .failedToday(failedToday)
                .build();
    }

    @Transactional(readOnly = true)
    public List<JobRecordDto> getAllJobs() {
        return jpaSplitTaskRepository.findAll().stream()
                .map(this::mapToJobRecordDto)
                .sorted(Comparator.comparing(JobRecordDto::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    private JobRecordDto mapToJobRecordDto(JpaSplitTaskEntity entity) {
        return JobRecordDto.builder()
                .id(entity.getTaskId())
                .taskId(entity.getTaskId())
                .taskType(entity.getTaskType())
                .novelId(entity.getNovelId())
                .fileName(entity.getFileName())
                .maxScenes(entity.getMaxScenes())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .progress(entity.getProgress())
                .message(entity.getMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .totalScenes(entity.getTotalScenes())
                .completedScenes(entity.getCompletedScenes())
                .build();
    }
}
