package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.model.Chapter;
import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChapterMapper {
    @Mapping(target = "novelId", source = "novel.id")
    @Mapping(target = "index", source = "indexNum")
    @Mapping(target = "startParagraphIndex", source = "startLine")
    @Mapping(target = "endParagraphIndex", source = "endLine")
    Chapter toDomain(JpaChapterEntity entity);

    @Mapping(target = "novel", ignore = true) // Handled in RepositoryImpl
    @Mapping(target = "indexNum", source = "index")
    @Mapping(target = "startLine", source = "startParagraphIndex")
    @Mapping(target = "endLine", source = "endParagraphIndex")
    @Mapping(target = "isDeleted", ignore = true)
    JpaChapterEntity toEntity(Chapter domain);
}