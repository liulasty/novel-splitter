package com.novel.splitter.application.controller;

import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.application.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "任务管理", description = "切分任务管理接口")
@CrossOrigin(origins = "*")
public class TaskController {

    private final TaskService taskService;

    @Autowired
    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "获取所有切分任务列表")
    public ResponseEntity<List<SplitTask>> getAllTasks() {
        List<SplitTask> tasks = taskService.getAllTasks();
        tasks.sort(Comparator.comparing(SplitTask::getCreatedAt).reversed());
        return ResponseEntity.ok(tasks);
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "获取单个切分任务状态")
    public ResponseEntity<SplitTask> getTask(@PathVariable String taskId) {
        SplitTask task = taskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "删除单个切分任务记录")
    public ResponseEntity<Void> deleteTask(@PathVariable String taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }
}
