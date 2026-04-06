package com.novel.splitter.application.controller;

import com.novel.splitter.application.model.dto.JobRecordDto;
import com.novel.splitter.application.model.dto.JobStatSummaryDto;
import com.novel.splitter.application.service.task.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@Tag(name = "任务监控", description = "作业监控统计接口")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class JobController {

    private final TaskService taskService;

    @GetMapping
    @Operation(summary = "获取所有作业记录")
    public List<JobRecordDto> getJobs() {
        return taskService.getAllJobs();
    }

    @GetMapping("/stats")
    @Operation(summary = "获取作业统计摘要")
    public JobStatSummaryDto getJobStats() {
        return taskService.getJobStats();
    }
}