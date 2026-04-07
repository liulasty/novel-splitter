package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaCleanupTaskEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CleanupTaskMapper {
    CleanupTaskMapper INSTANCE = Mappers.getMapper(CleanupTaskMapper.class);

    CleanupTask toDomain(JpaCleanupTaskEntity entity);

    JpaCleanupTaskEntity toEntity(CleanupTask domain);
}