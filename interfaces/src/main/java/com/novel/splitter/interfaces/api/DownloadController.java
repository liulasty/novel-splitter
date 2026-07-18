package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.service.download.DownloadService;
import com.novel.splitter.application.service.novel.NovelFacadeService;
import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.DownloadRequest;
import com.novel.splitter.application.model.dto.DownloadResponse;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * 小说下载控制器
 * 提供根据URL下载小说的功能
 */
@Tag(name = "小说下载管理", description = "提供根据URL下载小说的功能")
@RestController
@Profile("!prod")
@RequestMapping({"/api/v1/download", "/api/v1/download/"})
public class DownloadController {

    private final DownloadService downloadService;
    private final NovelFacadeService novelFacadeService;

    public DownloadController(DownloadService downloadService, NovelFacadeService novelFacadeService) {
        this.downloadService = downloadService;
        this.novelFacadeService = novelFacadeService;
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

    /**
     * 下载并启动小说入库处理
     *
     * @param request 下载并入库请求参数
     * @return 启动入库任务的响应信息
     */
    @Operation(summary = "小说下载并入库处理", description = "同步下载小说文件后异步启动入库流程（兼容入口，建议迁移到 /api/novels/{novelId}/pipeline）")
    @PostMapping("/ingest")
    public TaskSubmitResponseDto downloadAndIngest(@Valid @RequestBody DownloadAndIngestRequest request) throws IOException {
        return novelFacadeService.downloadAndIngest(request);
    }
}
