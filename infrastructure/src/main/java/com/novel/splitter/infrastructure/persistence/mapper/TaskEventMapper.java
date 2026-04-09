package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.TaskProgressEvent;
import com.novel.splitter.infrastructure.persistence.entity.JpaTaskEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TaskEventMapper {
    @Mapping(target = "timestamp", source = "createdAt")
    TaskProgressEvent toDomain(JpaTaskEventEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", source = "timestamp")
    JpaTaskEventEntity toEntity(TaskProgressEvent domain);
}