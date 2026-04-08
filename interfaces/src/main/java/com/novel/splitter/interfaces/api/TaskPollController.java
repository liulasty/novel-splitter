package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.task.SplitTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/tasks/poll")
@Tag(name = "任务轮询", description = "智能轮询任务状态接口")
@CrossOrigin(origins = "*")
public class TaskPollController {

    private final TaskCachePort taskCachePort;
    private final TaskService taskService;

    public TaskPollController(TaskCachePort taskCachePort, TaskService taskService) {
        this.taskCachePort = taskCachePort;
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(
            summary = "轮询任务状态",
            description = "支持 ids/taskIds（最多20个）或 novelId 兜底查询。novelId 路径返回非终态优先，其次近24小时终态。"
    )
    public List<PollResponse> pollTasks(
            @RequestParam(required = false, name = "ids") List<String> ids,
            @RequestParam(required = false, name = "taskIds") List<String> taskIds,
            @RequestParam(required = false, name = "novelId") String novelId) {
        List<String> rawIds = mergeIds(ids, taskIds);
        validateRequest(rawIds, novelId);
        List<String> normalizedIds = normalizeIds(rawIds);

        List<PollResponse> responses = new ArrayList<>();

        if (!normalizedIds.isEmpty()) {
            List<SplitTask> tasks = taskService.getTasksByIds(normalizedIds);
            for (SplitTask task : tasks) {
                String id = task.getTaskId();
                PollResponse cached = taskCachePort.get(id);
                if (cached != null) {
                    responses.add(cached);
                } else {
                    responses.add(buildPollResponse(task));
                }
            }
        } else if (novelId != null && !novelId.isEmpty()) {
            List<SplitTask> tasks = taskService.getRecentTasksByNovelId(novelId, 50);
            for (SplitTask task : tasks) {
                PollResponse cached = taskCachePort.get(task.getTaskId());
                if (cached != null) {
                    responses.add(cached);
                } else {
                    responses.add(buildPollResponse(task));
                }
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
        return mergedIds.stream()
                .toList();
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
