package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.DownloadService;
import com.novel.splitter.domain.model.dto.DownloadRequest;
import com.novel.splitter.domain.model.dto.DownloadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小说下载控制器
 * 提供根据URL下载小说的功能
 */
@Tag(name = "小说下载管理", description = "提供根据URL下载小说的功能")
@RestController
@RequestMapping({"/api/v1/download", "/api/v1/download/"})
public class DownloadController {

    private final DownloadService downloadService;

    public DownloadController(DownloadService downloadService) {
        this.downloadService = downloadService;
    }

    /**
     * 触发小说下载任务
     *
     * @param request 下载请求参数，包含小说URL和名称
     * @return 下载响应信息，包含成功状态和保存路径
     */
    @Operation(summary = "触发小说下载任务", description = "同步执行小说下载，返回下载结果及文件保存路径")
    @PostMapping
    public DownloadResponse triggerDownload(@RequestBody DownloadRequest request) {
        // 同步执行（注意：下载耗时较长，生产环境应异步）
        String savedPath = downloadService.downloadNovel(request.getUrl(), request.getName());
        return new DownloadResponse("Success", savedPath);
    }
}
