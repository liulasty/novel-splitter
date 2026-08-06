package com.novel.splitter.pipeline.etl;

import com.novel.splitter.core.ChapterRecognitionStrategy;
import com.novel.splitter.core.ChapterRecognitionStrategyRegistry;
import com.novel.splitter.core.ChapterRecognizer;
import com.novel.splitter.core.NovelLineNoiseFilter;
import com.novel.splitter.core.VolumeChapterRecognizer;
import com.novel.splitter.domain.enums.RecognitionStrategyType;
import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.RawParagraph;
import com.novel.splitter.domain.model.ChapterData;
import com.novel.splitter.domain.repository.NovelCacheRepository;
import com.novel.splitter.infrastructure.io.FileUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LocalNovelLoader {

    private final NovelCacheRepository novelCacheRepository;
    private final ChapterRecognitionStrategyRegistry strategyRegistry;

    public LocalNovelLoader(NovelCacheRepository novelCacheRepository,
                            ChapterRecognitionStrategyRegistry strategyRegistry) {
        this.novelCacheRepository = novelCacheRepository;
        this.strategyRegistry = strategyRegistry;
    }

    public Novel load(String novelId, Path path) throws IOException {
        return load(novelId, path, null, null);
    }

    public Novel load(String novelId, Path path, String chapterTitleRegex) throws IOException {
        return load(novelId, path, chapterTitleRegex, null);
    }

    /**
     * 按 {@link RecognitionStrategyType} 枚举分发章节识别；strategyType 为 null/空白/旧值 PLAIN 时默认 CN_CHAPTER。
     *
     * @param chapterTitleRegex 可选；仅 {@link RecognitionStrategyType#CUSTOM} 策略下作为<strong>整行匹配</strong>的 Java 正则
     * @param strategyType      识别策略字符串；未知值抛 {@link IllegalArgumentException}
     */
    public Novel load(String novelId, Path path, String chapterTitleRegex, String strategyType) throws IOException {
        RecognitionStrategyType strategyTypeEnum = RecognitionStrategyType.fromString(strategyType);
        ChapterRecognitionStrategy strategy = strategyRegistry.require(strategyTypeEnum, chapterTitleRegex);
        boolean isVolumeChapter = strategyTypeEnum == RecognitionStrategyType.VOLUME_CHAPTER;
        log.info("正在从 {} 加载小说（strategy: {}，custom regex: {}）",
                path, strategyTypeEnum, chapterTitleRegex != null && !chapterTitleRegex.isBlank());

        Pattern pattern = strategy.pattern();
        ChapterRecognizer chapterRecognizer = new ChapterRecognizer(pattern);
        VolumeChapterRecognizer volumeRecognizer = null;
        if (isVolumeChapter) {
            volumeRecognizer = new VolumeChapterRecognizer(pattern, null);
        }

        String fileName = path.getFileName().toString();
        String title = fileName.replace(".txt", "");
        String author = "Unknown";

        if (fileName.contains("-")) {
            String[] parts = fileName.replace(".txt", "").split("-");
            if (parts.length > 1) {
                author = parts[1];
            }
        }

        List<String> rawLines = FileUtils.readLinesAutoDetectEncoding(path);
        int lineOffset = ChapterRecognizer.skipLeadingTableOfContents(rawLines, pattern);
        if (lineOffset > 0) {
            log.info("已跳过开头 {} 行（目录 / 装饰性分隔块）", lineOffset);
        }

        List<Chapter> chapters = new ArrayList<>();
        List<RawParagraph> currentChapterParagraphs = new ArrayList<>();
        int currentWordCount = 0;
        String currentVolumeTitle = "";

        Chapter.ChapterBuilder currentChapterBuilder = null;
        boolean hasContentSinceLastTitle = false;

        for (int lineIndex = lineOffset; lineIndex < rawLines.size(); lineIndex++) {
            String line = rawLines.get(lineIndex);
            String content = ChapterRecognizer.stripLeadingUtf8Bom(line).trim();
            if (NovelLineNoiseFilter.shouldSkipParagraphLine(content)) {
                continue;
            }
            boolean isEmpty = content.isEmpty();

            // VOLUME_CHAPTER 模式：检测卷头行
            if (isVolumeChapter && !isEmpty && volumeRecognizer.isVolumeTitleLine(content)) {
                currentVolumeTitle = volumeRecognizer.extractVolumeName(content);
                continue;
            }

            if (!isEmpty && chapterRecognizer.isLikelyChapterTitle(content)) {
                if (currentChapterBuilder != null && hasContentSinceLastTitle) {
                    String fullTitle = isVolumeChapter && !currentVolumeTitle.isEmpty()
                            ? volumeRecognizer.buildFullChapterTitle(currentVolumeTitle,
                                    currentChapterBuilder.build().getTitle())
                            : currentChapterBuilder.build().getTitle();
                    String originalTitle = isVolumeChapter
                            ? (currentChapterBuilder.build().getTitle())
                            : null;
                    Chapter finishedChapter = Chapter.builder()
                            .index(chapters.size() + 1)
                            .title(fullTitle)
                            .volumeTitle(isVolumeChapter && !currentVolumeTitle.isEmpty() ? currentVolumeTitle : null)
                            .originalTitle(isVolumeChapter ? originalTitle : null)
                            .startParagraphIndex(currentChapterBuilder.build().getStartParagraphIndex())
                            .endParagraphIndex(lineIndex - 1)
                            .wordCount(currentWordCount)
                            .build();
                    chapters.add(finishedChapter);
                    if (novelId != null) {
                        saveChapterCache(novelId, finishedChapter.getIndex(), new ChapterData(finishedChapter, new ArrayList<>(currentChapterParagraphs)));
                    }
                    currentChapterParagraphs.clear();
                    currentWordCount = 0;
                }

                currentChapterBuilder = Chapter.builder()
                        .index(chapters.size() + 1)
                        .title(content)
                        .startParagraphIndex(lineIndex);
                hasContentSinceLastTitle = false;
            } else if (!isEmpty) {
                hasContentSinceLastTitle = true;
            }

            currentChapterParagraphs.add(RawParagraph.builder()
                    .index(lineIndex)
                    .content(content)
                    .isEmpty(isEmpty)
                    .build());
            if (!isEmpty) {
                currentWordCount += content.replaceAll("\\s+", "").length();
            }
        }

        if (currentChapterBuilder == null && !currentChapterParagraphs.isEmpty()) {
            RawParagraph first = currentChapterParagraphs.get(0);
            RawParagraph last = currentChapterParagraphs.get(currentChapterParagraphs.size() - 1);
            Chapter synthetic = Chapter.builder()
                    .index(1)
                    .title("全文")
                    .startParagraphIndex(first.getIndex())
                    .endParagraphIndex(last.getIndex())
                    .wordCount(currentWordCount)
                    .build();
            chapters.add(synthetic);
            if (novelId != null) {
                saveChapterCache(novelId, 1, new ChapterData(synthetic, new ArrayList<>(currentChapterParagraphs)));
            }
            log.info("未匹配到任何章节标题，已保存为单章 \"全文\"（共 {} 段）", currentChapterParagraphs.size());
        }

        if (currentChapterBuilder != null) {
            int endIdx = rawLines.isEmpty() ? lineOffset : rawLines.size() - 1;
            String finalFullTitle = isVolumeChapter && !currentVolumeTitle.isEmpty()
                    ? volumeRecognizer.buildFullChapterTitle(currentVolumeTitle,
                            currentChapterBuilder.build().getTitle())
                    : currentChapterBuilder.build().getTitle();
            String finalOriginalTitle = isVolumeChapter
                    ? currentChapterBuilder.build().getTitle()
                    : null;
            Chapter finishedChapter = Chapter.builder()
                    .index(chapters.size() + 1)
                    .title(finalFullTitle)
                    .volumeTitle(isVolumeChapter && !currentVolumeTitle.isEmpty() ? currentVolumeTitle : null)
                    .originalTitle(isVolumeChapter ? finalOriginalTitle : null)
                    .startParagraphIndex(currentChapterBuilder.build().getStartParagraphIndex())
                    .endParagraphIndex(endIdx)
                    .wordCount(currentWordCount)
                    .build();
            chapters.add(finishedChapter);
            if (novelId != null) {
                saveChapterCache(novelId, finishedChapter.getIndex(), new ChapterData(finishedChapter, new ArrayList<>(currentChapterParagraphs)));
            }
        }

        log.info("已加载小说 '{}'，作者 '{}'，共 {} 章", title, author, chapters.size());

        return Novel.builder()
                .title(title)
                .author(author)
                .chapters(chapters)
                .paragraphs(new ArrayList<>())
                .build();
    }

    /**
     * 尽力而为地写入章节缓存文件（chapter_N.json）。
     * <p>缓存是可重建产物：写失败仅记日志，不中断基准解析——基准完整性由 DB chapters 的原子落库保证。</p>
     */
    private void saveChapterCache(String novelId, int chapterIndex, ChapterData chapterData) {
        try {
            novelCacheRepository.saveChapter(novelId, chapterIndex, chapterData);
        } catch (Exception e) {
            log.warn("章节缓存写入失败（可重建，不影响基准）：novelId={} chapter={} err={}",
                    novelId, chapterIndex, e.toString());
        }
    }
}
