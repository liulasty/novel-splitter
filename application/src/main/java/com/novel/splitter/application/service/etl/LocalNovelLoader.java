package com.novel.splitter.application.service.etl;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.RawParagraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class LocalNovelLoader {

    // Matches "第123章 标题" or "第一章 标题"
    // Handles spaces: "第 1 章"
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^\\s*第\\s*[0-9零一二三四五六七八九十百千万]+\s*[章回].*");

    public Novel load(Path path) throws IOException {
        log.info("Loading novel from: {}", path);
        String fileName = path.getFileName().toString();
        String title = fileName.replace(".txt", "");
        String author = "Unknown"; 
        
        if (title.contains("-")) {
            String[] parts = title.split("-");
            title = parts[0];
            author = parts.length > 1 ? parts[1] : "Unknown";
        }

        List<Chapter> chapters = new ArrayList<>();
        List<RawParagraph> paragraphs = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineIndex = 0;
            Chapter.ChapterBuilder currentChapterBuilder = null;
            
            while ((line = reader.readLine()) != null) {
                // Clean content: trim whitespace
                String content = line.trim(); 
                boolean isEmpty = content.isEmpty();
                
                // Check for chapter title
                if (!isEmpty && CHAPTER_PATTERN.matcher(content).matches()) {
                    // Close previous chapter
                    if (currentChapterBuilder != null) {
                         chapters.add(currentChapterBuilder
                                 .endParagraphIndex(lineIndex - 1)
                                 .build());
                    }
                    
                    // Start new chapter
                    currentChapterBuilder = Chapter.builder()
                            .index(chapters.size() + 1)
                            .title(content)
                            .startParagraphIndex(lineIndex);
                }
                
                // Add paragraph
                paragraphs.add(RawParagraph.builder()
                        .index(lineIndex)
                        .content(content)
                        .isEmpty(isEmpty)
                        .build());
                
                lineIndex++;
            }
            
            // Close last chapter
            if (currentChapterBuilder != null) {
                chapters.add(currentChapterBuilder
                        .endParagraphIndex(lineIndex - 1)
                        .build());
            }
        }
        
        log.info("Loaded novel '{}' by '{}'. Chapters: {}, Paragraphs: {}", title, author, chapters.size(), paragraphs.size());
        
        return Novel.builder()
                .title(title)
                .author(author)
                .chapters(chapters)
                .paragraphs(paragraphs)
                .build();
    }
}
