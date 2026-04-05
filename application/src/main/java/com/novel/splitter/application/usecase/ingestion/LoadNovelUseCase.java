package com.novel.splitter.application.usecase.ingestion;

import com.novel.splitter.application.service.etl.LocalNovelLoader;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.infrastructure.progress.IngestProgress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.function.BiConsumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class LoadNovelUseCase {
    
    private final LocalNovelLoader novelLoader;

    public Novel load(String taskId, Path novelPath, BiConsumer<Integer, String> progressCallback) throws Exception {
        log.info("=== Start Load Phase for: {} (taskId: {}) ===", novelPath, taskId);
        progressCallback.accept(IngestProgress.LOAD_START, "开始读取文件...");
        Novel novel = novelLoader.load(taskId, novelPath);
        progressCallback.accept(IngestProgress.LOAD_END, String.format("文件读取完成，共 %d 个章节", novel.getChapters().size()));
        return novel;
    }
}
