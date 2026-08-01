package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.NovelSummaryListScope;
import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
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

    TaskSubmitResponseDto downloadAndIngest(DownloadAndIngestRequest request) throws IOException;

    List<NovelStatRecordDto> getNovelStats();

    List<ChapterDto> getChapters(String novelId);

    com.novel.splitter.domain.model.paging.PagedResult<SceneDto> getScenesByChapter(String novelId, Long chapterId, String version, int page, int size);

    void softDeleteNovel(String novelId);
}