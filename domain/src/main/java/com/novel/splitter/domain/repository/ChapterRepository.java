package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.model.Chapter;

import java.util.List;

public interface ChapterRepository {
    void saveAll(List<Chapter> chapters);
    List<Chapter> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}