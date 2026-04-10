package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.command.UploadNovelCommand;
import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.application.model.dto.NovelPipelineRequestDto;
import com.novel.splitter.application.model.dto.NovelSummaryDto;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.SplitRetryRequestDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;

import java.io.IOException;
import java.util.List;

public interface NovelFacadeService {

    List<String> listNovels() throws IOException;

    List<NovelSummaryDto> listNovelsFromDb();

    NovelUploadResponseDto uploadNovel(UploadNovelCommand command) throws IOException;

    TaskSubmitResponseDto split(String novelId, IngestRequest request) throws IOException;

    /**
     * 手动重试：从 Split 阶段重新触发（不重跑 Load），要求 chapters + parsed JSON 均已存在。
     */
    TaskSubmitResponseDto retrySplit(String novelId, SplitRetryRequestDto request) throws IOException;

    TaskSubmitResponseDto embed(String novelId) throws IOException;

    TaskSubmitResponseDto pipeline(String novelId, NovelPipelineRequestDto request) throws IOException;

    TaskSubmitResponseDto ingest(IngestRequest request) throws IOException;

    TaskSubmitResponseDto downloadAndIngest(DownloadAndIngestRequest request) throws IOException;

    List<NovelStatRecordDto> getNovelStats();

    List<ChapterDto> getChapters(String novelId);

    com.novel.splitter.domain.model.paging.PagedResult<SceneDto> getScenesByChapter(String novelId, Long chapterId, int page, int size);

    void softDeleteNovel(String novelId);
}