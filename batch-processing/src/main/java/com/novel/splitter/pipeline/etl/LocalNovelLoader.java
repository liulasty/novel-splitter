package com.novel.splitter.pipeline.etl;

import com.novel.splitter.core.ChapterRecognizer;
import com.novel.splitter.core.NovelLineNoiseFilter;
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

    public LocalNovelLoader(NovelCacheRepository novelCacheRepository) {
        this.novelCacheRepository = novelCacheRepository;
    }

    public Novel load(String novelId, Path path) throws IOException {
        return load(novelId, path, null);
    }

    /**
     * @param chapterTitleRegex 可选；非空时作为<strong>整行匹配</strong>的 Java 正则覆盖默认章节标题规则
     */
    public Novel load(String novelId, Path path, String chapterTitleRegex) throws IOException {
        log.info("Loading novel from: {} (custom chapter regex: {})", path, chapterTitleRegex != null && !chapterTitleRegex.isBlank());
        Pattern pattern = ChapterRecognizer.compileUserPattern(chapterTitleRegex);
        ChapterRecognizer chapterRecognizer = new ChapterRecognizer(pattern);

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
            log.info("Skipped {} leading lines as table-of-contents / decorative block", lineOffset);
        }

        List<Chapter> chapters = new ArrayList<>();
        List<RawParagraph> currentChapterParagraphs = new ArrayList<>();
        int currentWordCount = 0;

        Chapter.ChapterBuilder currentChapterBuilder = null;
        boolean hasContentSinceLastTitle = false;

        for (int lineIndex = lineOffset; lineIndex < rawLines.size(); lineIndex++) {
            String line = rawLines.get(lineIndex);
            String content = ChapterRecognizer.stripLeadingUtf8Bom(line).trim();
            if (NovelLineNoiseFilter.shouldSkipParagraphLine(content)) {
                continue;
            }
            boolean isEmpty = content.isEmpty();

            if (!isEmpty && chapterRecognizer.isLikelyChapterTitle(content)) {
                if (currentChapterBuilder != null && hasContentSinceLastTitle) {
                    Chapter finishedChapter = currentChapterBuilder
                            .endParagraphIndex(lineIndex - 1)
                            .wordCount(currentWordCount)
                            .build();
                    chapters.add(finishedChapter);
                    if (novelId != null) {
                        novelCacheRepository.saveChapter(novelId, finishedChapter.getIndex(), new ChapterData(finishedChapter, new ArrayList<>(currentChapterParagraphs)));
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
                novelCacheRepository.saveChapter(novelId, 1, new ChapterData(synthetic, new ArrayList<>(currentChapterParagraphs)));
            }
            log.info("No chapter headings matched; saved as single chapter \"全文\" ({} paragraphs)", currentChapterParagraphs.size());
        }

        if (currentChapterBuilder != null) {
            int endIdx = rawLines.isEmpty() ? lineOffset : rawLines.size() - 1;
            Chapter finishedChapter = currentChapterBuilder
                    .endParagraphIndex(endIdx)
                    .wordCount(currentWordCount)
                    .build();
            chapters.add(finishedChapter);
            if (novelId != null) {
                novelCacheRepository.saveChapter(novelId, finishedChapter.getIndex(), new ChapterData(finishedChapter, new ArrayList<>(currentChapterParagraphs)));
            }
        }

        log.info("Loaded novel '{}' by '{}'. Chapters: {}", title, author, chapters.size());

        return Novel.builder()
                .title(title)
                .author(author)
                .chapters(chapters)
                .paragraphs(new ArrayList<>())
                .build();
    }
}
