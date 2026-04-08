package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.application.model.dto.NovelUploadResponseDto;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.model.dto.SceneDto;
import com.novel.splitter.application.model.dto.TaskSubmitResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface NovelFacadeService {

    List<String> listNovels() throws IOException;

    NovelUploadResponseDto uploadNovel(MultipartFile file, String title, String author, String description) throws IOException;

    TaskSubmitResponseDto split(String novelId, IngestRequest request) throws IOException;

    TaskSubmitResponseDto embed(String novelId) throws IOException;

    TaskSubmitResponseDto ingest(IngestRequest request) throws IOException;

    TaskSubmitResponseDto downloadAndIngest(DownloadAndIngestRequest request) throws IOException;

    List<NovelStatRecordDto> getNovelStats();

    List<ChapterDto> getChapters(String novelId);

    List<SceneDto> getScenesByChapter(String novelId, Long chapterId);
}