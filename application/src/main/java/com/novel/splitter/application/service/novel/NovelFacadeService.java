package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.domain.model.dto.IngestRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface NovelFacadeService {

    List<String> listNovels() throws IOException;

    Map<String, String> uploadNovel(MultipartFile file, String title, String author, String description) throws IOException;

    Map<String, String> split(String novelId, IngestRequest request) throws IOException;

    Map<String, String> embed(String novelId) throws IOException;

    Map<String, String> downloadAndIngest(DownloadAndIngestRequest request) throws IOException;

    List<com.novel.splitter.domain.model.dto.NovelStatRecordDto> getNovelStats();

    List<com.novel.splitter.domain.entity.JpaChapterEntity> getChapters(String novelId);

    List<com.novel.splitter.domain.entity.JpaSceneEntity> getScenesByChapter(String novelId, Long chapterId);
}