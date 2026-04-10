package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import org.springframework.stereotype.Component;

@Component
public class ChapterMapper {
    public Chapter toDomain(JpaChapterEntity entity) {
        if (entity == null) return null;
        return Chapter.builder()
                .id(entity.getId())
                .novelId(entity.getNovel() != null ? entity.getNovel().getId() : null)
                .index(entity.getIndexNum())
                .title(entity.getTitle())
                .startParagraphIndex(entity.getStartLine())
                .endParagraphIndex(entity.getEndLine())
                .wordCount(entity.getWordCount())
                .build();
    }

    public JpaChapterEntity toEntity(Chapter domain) {
        if (domain == null) return null;
        JpaChapterEntity entity = new JpaChapterEntity();
        entity.setId(domain.getId());
        // entity.novel handled in repository impl
        entity.setTitle(domain.getTitle());
        entity.setIndexNum(domain.getIndex());
        entity.setStartLine(domain.getStartParagraphIndex());
        entity.setEndLine(domain.getEndParagraphIndex());
        entity.setWordCount(domain.getWordCount());
        // entity.isDeleted handled by repository layer
        return entity;
    }
}