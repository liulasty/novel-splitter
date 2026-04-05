package com.novel.splitter.application.service.novel;

import com.novel.splitter.domain.model.dto.DownloadAndIngestRequest;
import com.novel.splitter.domain.model.dto.IngestRequest;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface NovelFacadeService {

    List<String> listNovels() throws IOException;

    Map<String, String> uploadNovel(MultipartFile file) throws IOException;

    Map<String, String> ingest(IngestRequest request) throws IOException;

    Map<String, String> downloadAndIngest(DownloadAndIngestRequest request) throws IOException;
}
