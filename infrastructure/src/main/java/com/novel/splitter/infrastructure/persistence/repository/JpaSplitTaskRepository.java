package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaSplitTaskEntity;
import com.novel.splitter.domain.task.SplitTask.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaSplitTaskRepository extends JpaRepository<JpaSplitTaskEntity, String> {
    long countByStatus(TaskStatus status);
    long countByStatusAndUpdatedAtGreaterThanEqual(TaskStatus status, long timestamp);
}
