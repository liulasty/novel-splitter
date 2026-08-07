package com.novel.splitter.interfaces.api;

import com.novel.splitter.application.model.NovelSummaryListScope;
import com.novel.splitter.application.service.novel.NovelFacadeService;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.model.dto.LoadNovelRequestDto;
import com.novel.splitter.application.model.dto.NovelPipelineRequestDto;
import com.novel.splitter.application.model.dto.NovelSummaryDto;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.SceneSplitRequestDto;
import com.novel.splitter.application.model.dto.SplitRetryRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.model.dto.CreateVersionRequest;
import com.novel.splitter.application.model.dto.NovelVersionDto;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.ReparseChaptersRequestDto;
import com.novel.splitter.domain.model.paging.PagedResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @Operation(summary = "获取小说摘要列表", description = "按 scope 返回 DB 中的小说摘要：all=全部未软删记录；embed_ready=仅向量化已完成(COMPLETED)，用于 RAG/对话选书")
    @GetMapping("/summaries")
    public List<NovelSummaryDto> listNovelSummaries(
            @Parameter(description = "all | embed_ready", example = "all")
            @RequestParam(value = "scope", defaultValue = "all") String scope) {
        return novelFacadeService.listNovelSummaries(parseNovelSummaryScope(scope));
    }

    @Deprecated(since = "1.0", forRemoval = false)
    @Operation(summary = "获取小说列表(DB)", description = "兼容别名，等价于 GET /api/novels/summaries?scope=all")
    @GetMapping("/db")
    public List<NovelSummaryDto> listNovelsFromDb() {
        return novelFacadeService.listNovelSummaries(NovelSummaryListScope.ALL);
    }

    private static NovelSummaryListScope parseNovelSummaryScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return NovelSummaryListScope.ALL;
        }
        switch (raw.trim().toLowerCase()) {
            case "all":
                return NovelSummaryListScope.ALL;
            case "embed_ready":
                return NovelSummaryListScope.EMBED_READY;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope 必须是 all 或 embed_ready");
        }
    }

    @Operation(summary = "软删除小说", description = "将小说标记为删除，同时软删除其 chapters/scenes；后续可由 cleanup 任务做物理清理")
    @DeleteMapping("/{novelId}")
    public void softDeleteNovel(@PathVariable("novelId") String novelId) {
        novelFacadeService.softDeleteNovel(novelId);
    }

    /**
     * 上传小说文件
     *
     * @param file 待上传的文件
     * @return 包含上传结果和新文件名的响应实体
     */
    @Operation(summary = "上传小说文件", description = "上传本地小说文件到服务器存储目录，并自动生成唯一文件名")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public NovelUploadResponseDto uploadNovel(
            @Parameter(description = "上传的文件对象", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "小说标题") @RequestParam(value = "title", required = false) String title,
            @Parameter(description = "小说作者") @RequestParam(value = "author", required = false) String author,
            @Parameter(description = "小说描述") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "章节识别策略") @RequestParam(value = "strategy", required = false) String strategy,
            @Parameter(description = "章节标题正则（CUSTOM 策略）") @RequestParam(value = "chapterTitleRegex", required = false) String chapterTitleRegex) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        long size = file.getSize();
        if (size == 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件为空");
        }
        try (java.io.InputStream in = file.getInputStream()) {
            return novelFacadeService.uploadNovel(new UploadNovelCommand(in, file.getOriginalFilename(), title, author, description, size, strategy, chapterTitleRegex));
        }
    }

    @Operation(summary = "独立 Load", description = "仅解析原文为 chapters + parsed JSON，不自动进入切分；可选 force 强制重解析")
    @PostMapping("/{novelId}/load")
    public TaskSubmitResponseDto loadNovel(
            @PathVariable("novelId") String novelId,
            @RequestBody(required = false) LoadNovelRequestDto body) throws IOException {
        return novelFacadeService.load(novelId, body != null ? body : new LoadNovelRequestDto());
    }

    /**
     * 启动小说入库（解析、分块、向量化）处理
     *
     * @param request 入库请求参数
     * @return 启动入库任务的响应信息
     */
    @Operation(summary = "获取小说章节树", description = "获取小说的所有章节层级结构")
    @GetMapping("/{novelId}/chapters")
    public List<ChapterDto> getChapters(@PathVariable("novelId") String novelId) {
        return novelFacadeService.getChapters(novelId);
    }

    @Operation(summary = "获取章节片段", description = "获取某章节下的所有切分片段 (Scenes)；可选 version 过滤，不传则返回全部版本")
    @GetMapping("/{novelId}/chapters/{chapterId}/scenes")
    public PagedResult<SceneDto> getScenesByChapter(
            @PathVariable("novelId") String novelId,
            @PathVariable("chapterId") Long chapterId,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "200") int size) {
        return novelFacadeService.getScenesByChapter(novelId, chapterId, version, page, size);
    }
    @Operation(summary = "章节解析（CHAPTER_PARSE）", description = "投递 Load 队列：原文正则章节边界 → chapters 与 parsed JSON 落库；不自动场景切分，完成后请调用 scene-split")
    @PostMapping("/{novelId}/split")
    public TaskSubmitResponseDto splitNovel(@PathVariable("novelId") String novelId, @Valid @RequestBody IngestRequest request) throws IOException {
        return novelFacadeService.split(novelId, request);
    }

    @Operation(summary = "强制重解析章节", description = "清理旧章节/解析产物后重新 Load；可选 chapterTitleRegex 覆盖默认章节标题规则")
    @PostMapping("/{novelId}/re-parse-chapters")
    public TaskSubmitResponseDto reparseChapters(
            @PathVariable("novelId") String novelId,
            @RequestBody(required = false) ReparseChaptersRequestDto body) throws IOException {
        return novelFacadeService.reparseChapters(novelId, body != null ? body : new ReparseChaptersRequestDto());
    }

    @Operation(summary = "场景切分（SCENE_SPLIT）", description = "投递 Split 队列：需已完成章节解析。每次任务会生成多条 Scene；按 version 分区落库，任务前会删除该小说同名 version 的旧场景与向量。chunk 规则不会自动改变 version，不同滑窗策略并存请使用不同 version。"
            + " 若小说状态为 EMBEDDING（向量化进行中），拒绝提交并返回 409，避免与向量化并发冲突。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "任务已提交"),
            @ApiResponse(responseCode = "409", description = "小说正在向量化（EMBEDDING），暂不可发起场景切分")
    })
    @PostMapping("/{novelId}/scene-split")
    public TaskSubmitResponseDto sceneSplitNovel(
            @PathVariable("novelId") String novelId,
            @RequestBody(required = false) SceneSplitRequestDto request) throws IOException {
        return novelFacadeService.sceneSplit(novelId, request != null ? request : new SceneSplitRequestDto());
    }

    @Operation(summary = "重试场景切分（跳过章节解析）", description = "当 SplitWorker 失败时可重试；要求 chapters 与 chapter JSON 均已存在。若小说为 EMBEDDING，返回 409。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "任务已提交"),
            @ApiResponse(responseCode = "409", description = "小说正在向量化（EMBEDDING），暂不可发起场景切分")
    })
    @PostMapping("/{novelId}/split/retry")
    public TaskSubmitResponseDto retrySplitNovel(@PathVariable("novelId") String novelId, @RequestBody SplitRetryRequestDto request) throws IOException {
        return novelFacadeService.retrySplit(novelId, request);
    }

    @Operation(summary = "触发小说处理流水线", description = "stages 含 SPLIT 时默认仅章节解析（Load）；场景切分请用 /scene-split。stages 仅 EMBED 时向量化。splitEntry=SCENE_ONLY 时直接场景切分。"
            + " 当 splitEntry=SCENE_ONLY 且小说为 EMBEDDING 时返回 409。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "任务已提交"),
            @ApiResponse(responseCode = "409", description = "（仅 splitEntry=SCENE_ONLY 场景切分时）小说正在向量化（EMBEDDING），暂不可发起场景切分")
    })
    @PostMapping("/{novelId}/pipeline")
    public TaskSubmitResponseDto triggerPipeline(@PathVariable("novelId") String novelId, @Valid @RequestBody NovelPipelineRequestDto request) throws IOException {
        return novelFacadeService.pipeline(novelId, request);
    }

    @Operation(summary = "启动小说向量化", description = "触发已切分小说的异步向量化入库流程；同一 version 下有多套滑窗分区时需传 chunkSize/chunkOverlap")
    @PostMapping("/{novelId}/embed")
    public TaskSubmitResponseDto embedNovel(
            @PathVariable("novelId") String novelId,
            @Parameter(description = "切分版本，默认 v1") @RequestParam(value = "version", required = false) String version,
            @Parameter(description = "场景滑窗块大小，与场景切分一致") @RequestParam(value = "chunkSize", required = false) Integer chunkSize,
            @Parameter(description = "块重叠") @RequestParam(value = "chunkOverlap", required = false) Integer chunkOverlap)
            throws IOException {
        return novelFacadeService.embed(novelId, version, chunkSize, chunkOverlap);
    }

    @Deprecated
    @Operation(summary = "小说一键入库(已废弃)", description = "异步启动指定小说文件的解析、分块及向量化入库流程")
    @PostMapping("/ingest")
    public TaskSubmitResponseDto ingest(@Valid @RequestBody IngestRequest request) throws IOException {
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

    @Operation(summary = "获取小说版本列表", description = "返回该小说的全部版本，并按 novel.activeVersionTag 标注 active")
    @GetMapping("/{novelId}/versions")
    public List<NovelVersionDto> listVersions(@PathVariable("novelId") String novelId) {
        return novelFacadeService.listVersions(novelId);
    }

    @Operation(summary = "创建小说版本", description = "创建 PENDING 版本；versionTag 为空自动递增 v{n+1}；splitStrategy 非法返回 400")
    @PostMapping("/{novelId}/versions")
    public NovelVersionDto createVersion(
            @PathVariable("novelId") String novelId,
            @RequestBody(required = false) CreateVersionRequest request) {
        return novelFacadeService.createVersion(novelId, request != null ? request : new CreateVersionRequest());
    }

    @Operation(summary = "基线解析（阶段一）", description = "投递 Load 队列强制重解析章节为 chapters + parsed JSON，不自动场景切分；完成后调用版本 split")
    @PostMapping("/{novelId}/baseline")
    public TaskSubmitResponseDto baselineParse(
            @PathVariable("novelId") String novelId,
            @RequestBody(required = false) ReparseChaptersRequestDto request) throws IOException {
        return novelFacadeService.baselineParse(novelId, request != null ? request : new ReparseChaptersRequestDto());
    }

    @Operation(summary = "版本场景切分", description = "用版本自身 chunk 参数投 Split 队列（不自动串联向量化）")
    @PostMapping("/{novelId}/versions/{versionTag}/split")
    public TaskSubmitResponseDto startVersionSplit(
            @PathVariable("novelId") String novelId,
            @PathVariable("versionTag") String versionTag) throws IOException {
        return novelFacadeService.startVersionSplit(novelId, versionTag);
    }

    @Operation(summary = "版本向量化", description = "用版本自身 chunk 参数启动 EMBED 编排")
    @PostMapping("/{novelId}/versions/{versionTag}/embed")
    public TaskSubmitResponseDto startVersionEmbed(
            @PathVariable("novelId") String novelId,
            @PathVariable("versionTag") String versionTag) throws IOException {
        return novelFacadeService.startVersionEmbed(novelId, versionTag);
    }

    @Operation(summary = "激活版本", description = "将指定版本激活为检索所用活跃版本（仅 EMBED_DONE 可激活）")
    @PostMapping("/{novelId}/versions/{versionTag}/activate")
    public void activateVersion(
            @PathVariable("novelId") String novelId,
            @PathVariable("versionTag") String versionTag) {
        novelFacadeService.activateVersion(novelId, versionTag);
    }

    @Operation(summary = "删除版本", description = "删除该版本切分数据集/向量，并删除 novel_version 行")
    @DeleteMapping("/{novelId}/versions/{versionTag}")
    public void deleteVersion(
            @PathVariable("novelId") String novelId,
            @PathVariable("versionTag") String versionTag) {
        novelFacadeService.deleteVersion(novelId, versionTag);
    }

    @Operation(summary = "清除版本语义分析（回退至 0%）",
            description = "清除该版本全部场景的 role/characters/location/time，使 enrich 回到 0% 合法状态（不可逆），可立即向量化或重新分析")
    @DeleteMapping("/{novelId}/versions/{versionTag}/enrich")
    public void clearVersionEnrich(
            @PathVariable("novelId") String novelId,
            @PathVariable("versionTag") String versionTag) {
        novelFacadeService.resetVersionEnrich(novelId, versionTag);
    }

    @Operation(summary = "触发语义抽取（re-enrich）", description = "对指定版本的全部场景投递 enrich 消息，LLM 抽取 characters/location/time/role")
    @PostMapping("/{novelId}/re-enrich")
    public void reEnrich(@PathVariable("novelId") String novelId,
                         @RequestParam(value = "version", required = false) String version) {
        novelFacadeService.reEnrich(novelId, version);
    }

    @Operation(summary = "获取章节识别策略列表", description = "返回系统内置的所有章节识别策略，供前端下拉选择")
    @GetMapping("/chapter-strategies")
    public List<Map<String, String>> listChapterStrategies() {
        List<Map<String, String>> strategies = new ArrayList<>();

        Map<String, String> plain = new LinkedHashMap<>();
        plain.put("key", "CN_CHAPTER");
        plain.put("label", "普通章节");
        plain.put("description", "仅识别 [第X章] 格式，适用于常规无分卷小说");
        strategies.add(plain);

        Map<String, String> volume = new LinkedHashMap<>();
        volume.put("key", "VOLUME_CHAPTER");
        volume.put("label", "分卷章节");
        volume.put("description", "识别 [卷：标题] + [第X章]，自动拼合全局唯一章节名");
        strategies.add(volume);

        Map<String, String> custom = new LinkedHashMap<>();
        custom.put("key", "CUSTOM");
        custom.put("label", "自定义正则");
        custom.put("description", "自行输入整行匹配正则，适配特殊章节格式");
        strategies.add(custom);

        return strategies;
    }
}
