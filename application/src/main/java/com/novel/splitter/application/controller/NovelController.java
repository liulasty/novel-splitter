package com.novel.splitter.application.controller;

import com.novel.splitter.application.service.novel.NovelFacadeService;
import com.novel.splitter.domain.model.dto.IngestRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.novel.splitter.domain.model.dto.NovelStatRecordDto;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 小说文件管理控制器
 * 提供小说的列表查询、上传及入库（向量化）处理功能
 */
@Tag(name = "小说文件管理", description = "提供小说的列表查询、上传及入库（向量化）处理功能")
@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
public class NovelController {

    private final NovelFacadeService novelFacadeService;

    /**
     * 列出所有可用的本地小说文件
     *
     * @return 小说文件名列表
     */
    @Operation(summary = "获取小说列表", description = "列出服务器本地存储目录下的所有 .txt 格式小说文件")
    @GetMapping
    public List<String> listNovels() throws IOException {
        return novelFacadeService.listNovels();
    }

    /**
     * 上传小说文件
     *
     * @param file 待上传的文件
     * @return 包含上传结果和新文件名的响应实体
     */
    @Operation(summary = "上传小说文件", description = "上传本地小说文件到服务器存储目录，并自动生成唯一文件名")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Map<String, String> uploadNovel(
            @Parameter(description = "上传的文件对象", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "小说标题") @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "小说作者") @RequestParam(value = "author", required = false) String author,
            @Parameter(description = "小说描述") @RequestParam(value = "description", required = false) String description) throws IOException {
        return novelFacadeService.uploadNovel(file, title, author, description);
    }

    /**
     * 启动小说入库（解析、分块、向量化）处理
     *
     * @param request 入库请求参数
     * @return 启动入库任务的响应信息
     */
    @Operation(summary = "获取小说章节树", description = "获取小说的所有章节层级结构")
    @GetMapping("/{novelId}/chapters")
    public List<com.novel.splitter.domain.model.Chapter> getChapters(@PathVariable String novelId) {
        return novelFacadeService.getChapters(novelId);
    }

    @Operation(summary = "获取章节片段", description = "获取某章节下的所有切分片段 (Scenes)")
    @GetMapping("/{novelId}/chapters/{chapterId}/scenes")
    public List<com.novel.splitter.domain.model.Scene> getScenesByChapter(@PathVariable String novelId, @PathVariable Long chapterId) {
        return novelFacadeService.getScenesByChapter(novelId, chapterId);
    }
    @PostMapping("/{novelId}/split")
    public Map<String, String> splitNovel(@PathVariable String novelId, @Valid @RequestBody IngestRequest request) throws IOException {
        return novelFacadeService.split(novelId, request);
    }

    @Operation(summary = "启动小说向量化", description = "触发已切分小说的异步向量化入库流程")
    @PostMapping("/{novelId}/embed")
    public Map<String, String> embedNovel(@PathVariable String novelId) throws IOException {
        return novelFacadeService.embed(novelId);
    }

    @Deprecated
    @Operation(summary = "小说一键入库(已废弃)", description = "异步启动指定小说文件的解析、分块及向量化入库流程")
    @PostMapping("/ingest")
    public Map<String, String> ingest(@Valid @RequestBody IngestRequest request) throws IOException {
        return novelFacadeService.ingest(request);
    }

    /**
     * 获取所有小说的入库统计信息
     *
     * @return 小说统计信息列表
     */
    @Operation(summary = "获取小说统计信息", description = "返回每本小说的版本、分块数、向量数及入库状态")
    @GetMapping("/stats")
    public List<NovelStatRecordDto> getNovelStats() {
        return novelFacadeService.getNovelStats();
    }
}
