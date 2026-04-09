package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.task.CleanupTask;
import com.novel.splitter.infrastructure.persistence.entity.JpaCleanupTaskEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CleanupTaskMapper {
    CleanupTask toDomain(JpaCleanupTaskEntity entity);

    JpaCleanupTaskEntity toEntity(CleanupTask domain);
}