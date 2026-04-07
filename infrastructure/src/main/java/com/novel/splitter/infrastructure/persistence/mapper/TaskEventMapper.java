package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.infrastructure.persistence.entity.JpaTaskEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TaskEventMapper {
    TaskEventMapper INSTANCE = Mappers.getMapper(TaskEventMapper.class);

    @Mapping(target = "timestamp", source = "createdAt")
    TaskProgressEvent toDomain(JpaTaskEventEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", source = "timestamp")
    JpaTaskEventEntity toEntity(TaskProgressEvent domain);
}