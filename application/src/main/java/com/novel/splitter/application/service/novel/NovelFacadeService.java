package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.NovelSummaryListScope;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.application.model.dto.NovelPipelineRequestDto;
import com.novel.splitter.application.model.dto.NovelSummaryDto;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.LoadNovelRequestDto;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.SceneSplitRequestDto;
import com.novel.splitter.application.model.dto.ReparseChaptersRequestDto;
import com.novel.splitter.application.model.dto.SplitRetryRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import com.novel.splitter.application.model.dto.CreateVersionRequest;
import com.novel.splitter.application.model.dto.NovelVersionDto;

import java.io.IOException;
import java.util.List;

public interface NovelFacadeService {

    List<String> listNovels() throws IOException;

    List<NovelSummaryDto> listNovelSummaries(NovelSummaryListScope scope);

    NovelUploadResponseDto uploadNovel(UploadNovelCommand command) throws IOException;

    /**
     * 章节解析：投递 Load 队列，完成后仅落库章节与解析产物，不自动场景切分。
     */
    TaskSubmitResponseDto split(String novelId, IngestRequest request) throws IOException;

    /**
     * 场景切分：投递 Split 队列（需已完成章节结构化）；可选串联向量化。
     */
    TaskSubmitResponseDto sceneSplit(String novelId, SceneSplitRequestDto request) throws IOException;

    /**
     * 手动重试场景切分（不重跑章节解析），要求 chapters + parsed JSON 均已存在。
     */
    TaskSubmitResponseDto retrySplit(String novelId, SplitRetryRequestDto request) throws IOException;

    TaskSubmitResponseDto embed(String novelId) throws IOException;

    TaskSubmitResponseDto embed(String novelId, String version) throws IOException;

    /**
     * 向量化：指定业务 version 与滑窗分区；chunk 参数为空时由队列消费者在仅存在单一分区时自动推断。
     */
    TaskSubmitResponseDto embed(String novelId, String version, Integer chunkSize, Integer chunkOverlap) throws IOException;

    /**
     * 独立 Load：解析原文为 chapters + parsed JSON（不自动进入切分）。
     */
    TaskSubmitResponseDto load(String novelId, LoadNovelRequestDto request) throws IOException;

    TaskSubmitResponseDto pipeline(String novelId, NovelPipelineRequestDto request) throws IOException;

    TaskSubmitResponseDto ingest(IngestRequest request) throws IOException;

    /**
     * 强制重解析章节（清理旧产物后走 Load）；可选自定义章节标题正则。
     */
    TaskSubmitResponseDto reparseChapters(String novelId, ReparseChaptersRequestDto request) throws IOException;

    List<NovelStatRecordDto> getNovelStats();

    List<ChapterDto> getChapters(String novelId);

    com.novel.splitter.domain.model.paging.PagedResult<SceneDto> getScenesByChapter(String novelId, Long chapterId, String version, int page, int size);

    void softDeleteNovel(String novelId);

    /**
     * 列出小说全部版本，并按 {@code novel.activeVersionTag} 标注 {@code active}。
     */
    List<NovelVersionDto> listVersions(String novelId);

    /**
     * 创建版本（PENDING 状态）：versionTag 为空自动递增 v{n+1}；splitStrategy 非法抛 400；
     * chunkSize/chunkOverlap 为空取应用默认。
     */
    NovelVersionDto createVersion(String novelId, CreateVersionRequest request);

    /**
     * 版本场景切分：用版本自身 chunk 参数投 Split 队列（不自动串联 embed，触发由后续 embed 端点负责）。
     */
    TaskSubmitResponseDto startVersionSplit(String novelId, String versionTag) throws IOException;

    /**
     * 版本向量化：用版本自身 chunk 参数启动 EMBED 编排。
     */
    TaskSubmitResponseDto startVersionEmbed(String novelId, String versionTag) throws IOException;

    /**
     * 激活版本：委托 {@link NovelVersionService#activate}（原子切指针）。
     */
    void activateVersion(String novelId, String versionTag);

    /**
     * 删除版本：删除该版本切分数据集/向量，并删除 novel_version 行。
     */
    void deleteVersion(String novelId, String versionTag);

    /**
     * 基线解析（阶段一入口）：复用重解析逻辑投 Load 队列。
     */
    TaskSubmitResponseDto baselineParse(String novelId, ReparseChaptersRequestDto request) throws IOException;

    /**
     * 触发对指定版本全部场景的语义抽取（re-enrich）。version 为空时使用活动版本。
     */
    void reEnrich(String novelId, String version);

    /** 清除版本 enrich 数据（回退至 0%，不可逆），使版本回到可向量化状态。 */
    void resetVersionEnrich(String novelId, String versionTag);
}