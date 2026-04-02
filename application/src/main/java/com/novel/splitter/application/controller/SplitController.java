package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.SplitService;
import com.novel.splitter.domain.model.dto.SplitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小说分块处理控制器
 * 提供手动触发小说分块的任务接口
 */
@Tag(name = "小说分块管理", description = "提供手动触发小说分块的任务接口")
@RestController
@RequestMapping({"/api/v1/split", "/api/v1/split/"})
public class SplitController {

    private final SplitService splitService;

    public SplitController(SplitService splitService) {
        this.splitService = splitService;
    }

    /**
     * 触发小说分块任务
     *
     * @param request 分块请求参数，包含文件路径及版本号
     * @return 分块任务的执行结果消息
     */
    @Operation(summary = "触发小说分块任务", description = "同步执行小说文件的解析和分块处理")
    @PostMapping
    public String triggerSplit(@RequestBody SplitRequest request) {
        // 异步执行推荐使用线程池，这里为了简单演示同步执行
        splitService.executeSplit(request.getFilePath(), request.getVersion());
        return "Task completed for " + request.getFilePath();
    }
}
