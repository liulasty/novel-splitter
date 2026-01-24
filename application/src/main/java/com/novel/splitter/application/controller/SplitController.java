package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.SplitService;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/split", "/api/v1/split/"})
public class SplitController {

    private final SplitService splitService;

    public SplitController(SplitService splitService) {
        this.splitService = splitService;
    }

    @PostMapping
    public String triggerSplit(@RequestBody SplitRequest request) {
        // 异步执行推荐使用线程池，这里为了简单演示同步执行
        splitService.executeSplit(request.getFilePath(), request.getVersion());
        return "Task completed for " + request.getFilePath();
    }

    @Data
    public static class SplitRequest {
        private String filePath;
        private String version = "v1-rest";
    }
}
