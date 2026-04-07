package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.SplitTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaSplitTaskEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface SplitTaskMapper {
    SplitTaskMapper INSTANCE = Mappers.getMapper(SplitTaskMapper.class);

    @Mapping(target = "completedScenes", ignore = true) // Handle AtomicInteger manually if needed
    SplitTask toDomain(JpaSplitTaskEntity entity);

    @Mapping(target = "completedScenes", expression = "java(domain.getCompletedScenes().get())")
    JpaSplitTaskEntity toEntity(SplitTask domain);
}