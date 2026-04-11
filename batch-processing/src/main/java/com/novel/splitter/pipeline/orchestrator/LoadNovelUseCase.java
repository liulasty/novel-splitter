package com.novel.splitter.pipeline.orchestrator;

import com.novel.splitter.pipeline.etl.LocalNovelLoader;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.task.IngestProgress;
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

    public Novel load(String novelId, Path novelPath, BiConsumer<Integer, String> progressCallback) throws Exception {
        return load(novelId, novelPath, progressCallback, null);
    }

    /**
     * @param chapterTitleRegex 可选，覆盖默认章节标题正则（整行匹配）
     */
    public Novel load(
            String novelId,
            Path novelPath,
            BiConsumer<Integer, String> progressCallback,
            String chapterTitleRegex) throws Exception {
        log.info("=== Start Load Phase for: {} (novelId: {}) ===", novelPath, novelId);
        progressCallback.accept(IngestProgress.LOAD_START, "开始读取文件...");
        Novel novel = novelLoader.load(novelId, novelPath, chapterTitleRegex);
        progressCallback.accept(IngestProgress.LOAD_END, String.format("文件读取完成，共 %d 个章节", novel.getChapters().size()));
        return novel;
    }
}
