package com.novel.splitter.application.service.novel;

import com.novel.splitter.application.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.application.model.dto.IngestRequest;
import com.novel.splitter.application.model.dto.NovelStatRecordDto;
import com.novel.splitter.application.model.dto.ChapterDto;
import com.novel.splitter.application.model.dto.SceneDto;
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

    List<NovelStatRecordDto> getNovelStats();

    List<ChapterDto> getChapters(String novelId);

    List<ChapterDto> getChapters(String novelId, String version);

    List<SceneDto> getScenesByChapter(String novelId, Long chapterId);
}