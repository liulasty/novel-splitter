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
        return load(novelId, novelPath, progressCallback, chapterTitleRegex, null);
    }

    /**
     * @param chapterTitleRegex 可选，仅 CUSTOM 策略下覆盖章节标题正则
     * @param recognitionStrategy 识别策略字符串；null 默认 CN_CHAPTER，未知值抛 IllegalArgumentException
     */
    public Novel load(
            String novelId,
            Path novelPath,
            BiConsumer<Integer, String> progressCallback,
            String chapterTitleRegex,
            String recognitionStrategy) throws Exception {
        log.info("=== Start Load Phase for: {} (novelId: {}, strategy: {}) ===", novelPath, novelId,
                recognitionStrategy != null ? recognitionStrategy : "CN_CHAPTER");
        progressCallback.accept(IngestProgress.LOAD_START, "开始读取文件...");
        Novel novel = novelLoader.load(novelId, novelPath, chapterTitleRegex, recognitionStrategy);
        progressCallback.accept(IngestProgress.LOAD_END, String.format("文件读取完成，共 %d 个章节", novel.getChapters().size()));
        return novel;
    }
}
