package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface NovelMapper {
    NovelMapper INSTANCE = Mappers.getMapper(NovelMapper.class);

    // Map JPA Entity to Domain Model
    @Mapping(target = "chapters", ignore = true)
    @Mapping(target = "paragraphs", ignore = true)
    @Mapping(target = "isDeleted", source = "isDeleted")
    Novel toDomain(JpaNovelEntity entity);

    // Map Domain Model to JPA Entity
    @Mapping(target = "isDeleted", source = "isDeleted")
    JpaNovelEntity toEntity(Novel domain);
}