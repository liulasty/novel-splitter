package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.model.dto.SplitTaskPageDto;
import com.novel.splitter.application.model.dto.TaskProgressEventDto;
import com.novel.splitter.application.mapper.DtoMapper;
import com.novel.splitter.application.service.task.TaskQueryService;
import com.novel.splitter.application.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "切分任务管理接口")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final DtoMapper dtoMapper;
    private final TaskQueryService taskQueryService;

    public TaskController(TaskService taskService, DtoMapper dtoMapper, TaskQueryService taskQueryService) {
        this.taskService = taskService;
        this.dtoMapper = dtoMapper;
        this.taskQueryService = taskQueryService;
    }

    @GetMapping
    @Operation(summary = "获取所有切分任务列表")
    public List<SplitTaskDto> getAllTasks() {
        return taskQueryService.getAllTasksWithNovelTitle();
    }

    /** 使用 /list 避免与 /{taskId} 路径冲突（例如 taskId=paged） */
    @GetMapping("/list")
    @Operation(summary = "分页查询任务（可选筛选 novelId/taskType/status/时间范围）")
    public SplitTaskPageDto getTasksPage(
            @RequestParam(value = "novelId", required = false) String novelId,
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "updatedFrom", required = false) Long updatedFrom,
            @RequestParam(value = "updatedTo", required = false) Long updatedTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return taskQueryService.findTasksPage(novelId, taskType, status, updatedFrom, updatedTo, page, size);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "获取单个切分任务状态")
    public SplitTaskDto getTask(@PathVariable("taskId") String taskId) {
        SplitTaskDto dto = taskQueryService.getTaskWithNovelTitle(taskId);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId);
        }
        return dto;
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除单个切分任务记录", description = "仅删除任务记录与事件日志，不删除已入库章节、场景或向量。SUCCESS/FAILED 等已结束任务可删；PENDING/PROCESSING 返回 409。")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "已删除"),
            @ApiResponse(responseCode = "409", description = "任务运行中，不可删除")
    })
    public void deleteTask(@PathVariable("taskId") String taskId) {
        var task = taskService.getTask(taskId);
        if (task != null && (task.getStatus() == com.novel.splitter.domain.task.SplitTask.TaskStatus.PENDING
                || task.getStatus() == com.novel.splitter.domain.task.SplitTask.TaskStatus.PROCESSING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task is running; cannot delete. Please wait until it finishes.");
        }
        taskService.deleteTask(taskId);
    }

    @GetMapping("/{taskId}/events")
    @Operation(summary = "获取单个切分任务的历史事件日志")
    public List<TaskProgressEventDto> getTaskEvents(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "sinceTimestamp", required = false) Long sinceTimestamp) {
        return dtoMapper.toTaskProgressEventDtos(taskService.getTaskEvents(taskId, sinceTimestamp));
    }
}
