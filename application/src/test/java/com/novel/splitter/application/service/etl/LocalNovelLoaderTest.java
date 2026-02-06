package com.novel.splitter.application.service.etl;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.domain.model.Chapter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalNovelLoaderTest {

    @Test
    void loadRealNovel() throws IOException {
        // Arrange
        LocalNovelLoader loader = new LocalNovelLoader();
        Path path = Paths.get("d:\\soft\\novel-splitter\\data\\novel-storage\\九阳帝尊-剑棕.txt");

        // Act
        Novel novel = loader.load(path);

        // Assert
        assertNotNull(novel);
        System.out.println("Title: " + novel.getTitle());
        System.out.println("Author: " + novel.getAuthor());
        System.out.println("Total Paragraphs: " + novel.getParagraphs().size());
        System.out.println("Total Chapters: " + novel.getChapters().size());

        assertEquals("九阳帝尊", novel.getTitle());
        assertEquals("剑棕", novel.getAuthor());
        assertTrue(novel.getChapters().size() > 0, "Should have chapters");

        // Print first 5 chapters
        List<Chapter> chapters = novel.getChapters();
        int limit = Math.min(5, chapters.size());
        for (int i = 0; i < limit; i++) {
            Chapter ch = chapters.get(i);
            System.out.printf("Chapter %d: %s (Lines: %d-%d)%n", 
                    ch.getIndex(), ch.getTitle(), ch.getStartParagraphIndex(), ch.getEndParagraphIndex());
        }
        
        // Print first 10 paragraphs to check content
        System.out.println("--- First 10 Lines ---");
        for(int i=0; i<Math.min(10, novel.getParagraphs().size()); i++) {
            System.out.println(novel.getParagraphs().get(i).getContent());
        }
    }
}
