package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.PollResponse;
import com.novel.splitter.application.port.out.TaskCachePort;
import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.domain.task.SplitTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks/poll")
@Tag(name = "任务轮询", description = "智能轮询任务状态接口")
@CrossOrigin(origins = "*")
public class TaskPollController {

    private final TaskCachePort taskCachePort;
    private final TaskService taskService;

    @Autowired
    public TaskPollController(TaskCachePort taskCachePort, TaskService taskService) {
        this.taskCachePort = taskCachePort;
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "轮询任务状态")
    public List<PollResponse> pollTasks(
            @RequestParam(required = false) List<String> ids,
            @RequestParam(required = false) String novelId) {
            
        List<PollResponse> responses = new ArrayList<>();
        
        if (ids != null && !ids.isEmpty()) {
            int limit = Math.min(ids.size(), 20);
            for (int i = 0; i < limit; i++) {
                String id = ids.get(i);
                PollResponse cached = taskCachePort.get(id);
                if (cached != null) {
                    responses.add(cached);
                } else {
                    SplitTask task = taskService.getTask(id);
                    if (task != null) {
                        responses.add(buildPollResponse(task));
                    }
                }
            }
        } else if (novelId != null && !novelId.isEmpty()) {
            List<SplitTask> tasks = taskService.getAllTasks().stream()
                    .filter(t -> novelId.equals(t.getNovelId()))
                    .collect(Collectors.toList());
                    
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
