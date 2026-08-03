package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import org.springframework.stereotype.Component;

@Component
public class NovelMapper {
    public Novel toDomain(JpaNovelEntity entity) {
        if (entity == null) return null;
        return Novel.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .author(entity.getAuthor())
                .description(entity.getDescription())
                .coverUrl(entity.getCoverUrl())
                .filePath(entity.getFilePath())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .isDeleted(entity.isDeleted())
                .activeVersionTag(entity.getActiveVersionTag())
                .chapters(null)
                .paragraphs(null)
                .build();
    }

    public JpaNovelEntity toEntity(JpaNovelEntity entity, Novel domain) {
        if (entity == null || domain == null) return entity;
        entity.setId(domain.getId());
        entity.setTitle(domain.getTitle());
        entity.setAuthor(domain.getAuthor());
        entity.setDescription(domain.getDescription());
        entity.setCoverUrl(domain.getCoverUrl());
        entity.setFilePath(domain.getFilePath());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setDeleted(domain.isDeleted());
        entity.setActiveVersionTag(domain.getActiveVersionTag());
        return entity;
    }
}