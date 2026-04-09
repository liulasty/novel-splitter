package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.model.dto.TaskProgressEventDto;
import com.novel.splitter.application.mapper.DtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "切分任务管理接口")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final DtoMapper dtoMapper;

    public TaskController(TaskService taskService, DtoMapper dtoMapper) {
        this.taskService = taskService;
        this.dtoMapper = dtoMapper;
    }

    @GetMapping
    @Operation(summary = "获取所有切分任务列表")
    public List<SplitTaskDto> getAllTasks() {
        return dtoMapper.toSplitTaskDtos(taskService.getAllTasks());
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "获取单个切分任务状态")
    public SplitTaskDto getTask(@PathVariable("taskId") String taskId) {
        return dtoMapper.toSplitTaskDto(taskService.getTask(taskId));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除单个切分任务记录")
    public void deleteTask(@PathVariable("taskId") String taskId) {
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
