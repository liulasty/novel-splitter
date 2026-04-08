package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.model.Novel;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface NovelMapper {
    // Map JPA Entity to Domain Model
    @Mapping(target = "chapters", ignore = true)
    @Mapping(target = "paragraphs", ignore = true)
    @Mapping(target = "isDeleted", expression = "java(entity.isDeleted())")
    Novel toDomain(JpaNovelEntity entity);

    // Map Domain Model to JPA Entity
    @Mapping(target = "deleted", source = "domain.deleted")
    JpaNovelEntity toEntity(@MappingTarget JpaNovelEntity entity, Novel domain);
}