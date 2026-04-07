package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.task.TaskService;
import com.novel.splitter.application.service.task.TaskSseService;
import com.novel.splitter.application.model.dto.SplitTaskDto;
import com.novel.splitter.application.model.dto.TaskProgressEventDto;
import com.novel.splitter.application.mapper.DtoMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "切分任务管理接口")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;
    private final TaskSseService taskSseService;

    @Autowired
    public TaskController(TaskService taskService, TaskSseService taskSseService) {
        this.taskService = taskService;
        this.taskSseService = taskSseService;
    }

    @GetMapping
    @Operation(summary = "获取所有切分任务列表")
    public List<SplitTaskDto> getAllTasks() {
        return DtoMapper.INSTANCE.toSplitTaskDtos(taskService.getAllTasks());
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "获取单个切分任务状态")
    public SplitTaskDto getTask(@PathVariable String taskId) {
        return DtoMapper.INSTANCE.toSplitTaskDto(taskService.getTask(taskId));
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除单个切分任务记录")
    public void deleteTask(@PathVariable String taskId) {
        taskService.deleteTask(taskId);
    }

    @GetMapping("/{taskId}/stream")
    @Operation(summary = "建立 SSE 连接获取任务实时日志")
    public SseEmitter streamTaskProgress(@PathVariable String taskId) {
        return taskSseService.connect(taskId);
    }

    @GetMapping("/{taskId}/events")
    @Operation(summary = "获取单个切分任务的历史事件日志")
    public List<TaskProgressEventDto> getTaskEvents(
            @PathVariable String taskId,
            @RequestParam(required = false) Long sinceTimestamp) {
        return DtoMapper.INSTANCE.toTaskProgressEventDtos(taskService.getTaskEvents(taskId, sinceTimestamp));
    }
}
