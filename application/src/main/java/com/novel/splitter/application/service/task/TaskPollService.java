package com.novel.splitter.application.service.task;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import com.novel.splitter.domain.task.SplitTask;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TaskPollService {

    private final TaskCachePort taskCachePort;
    private final TaskService taskService;

    public TaskPollService(TaskCachePort taskCachePort, TaskService taskService) {
        this.taskCachePort = taskCachePort;
        this.taskService = taskService;
    }

    public List<PollResponse> pollTasks(List<String> ids, List<String> taskIds, String novelId) {
        List<String> rawIds = mergeIds(ids, taskIds);
        validateRequest(rawIds, novelId);
        List<String> normalizedIds = normalizeIds(rawIds);

        List<PollResponse> responses = new ArrayList<>();

        if (!normalizedIds.isEmpty()) {
            List<SplitTask> tasks = taskService.getTasksByIds(normalizedIds);
            for (SplitTask task : tasks) {
                String id = task.getTaskId();
                PollResponse cached = taskCachePort.get(id);
                responses.add(cached != null ? cached : buildPollResponse(task));
            }
            return responses;
        }

        if (novelId != null && !novelId.isEmpty()) {
            List<SplitTask> tasks = taskService.getRecentTasksByNovelId(novelId, 50);
            for (SplitTask task : tasks) {
                PollResponse cached = taskCachePort.get(task.getTaskId());
                responses.add(cached != null ? cached : buildPollResponse(task));
            }
        }

        return responses;
    }

    private List<String> mergeIds(List<String> ids, List<String> taskIds) {
        Set<String> merged = new LinkedHashSet<>();
        if (ids != null) {
            merged.addAll(ids);
        }
        if (taskIds != null) {
            merged.addAll(taskIds);
        }
        return merged.stream()
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    private List<String> normalizeIds(List<String> mergedIds) {
        if (mergedIds == null || mergedIds.isEmpty()) {
            return List.of();
        }
        return mergedIds.stream().toList();
    }

    private void validateRequest(List<String> rawIds, String novelId) {
        if ((rawIds == null || rawIds.isEmpty()) && (novelId == null || novelId.isBlank())) {
            throw new IllegalArgumentException("Either ids/taskIds or novelId is required");
        }
        if (rawIds != null && rawIds.size() > 20) {
            throw new IllegalArgumentException("At most 20 task IDs are allowed per polling request");
        }
    }

    private PollResponse buildPollResponse(SplitTask task) {
        return PollResponse.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus() != null ? task.getStatus().name() : "PENDING")
                .progress(task.getProgress())
                .message(task.getMessage())
                .updatedAt(task.getUpdatedAt())
                .serverTime(System.currentTimeMillis())
                .build();
    }
}

