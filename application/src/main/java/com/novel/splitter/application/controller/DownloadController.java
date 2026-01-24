package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.DownloadService;
import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/download", "/api/v1/download/"})
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    @PostMapping
    public DownloadResponse triggerDownload(@RequestBody DownloadRequest request) {
        // 同步执行（注意：下载耗时较长，生产环境应异步）
        String savedPath = downloadService.downloadNovel(request.getUrl(), request.getName());
        return new DownloadResponse("Success", savedPath);
    }

    @Data
    public static class DownloadRequest {
        private String url;
        private String name;
    }

    @Data
    public static class DownloadResponse {
        private String status;
        private String savedPath;

        public DownloadResponse(String status, String savedPath) {
            this.status = status;
            this.savedPath = savedPath;
        }
    }
}
