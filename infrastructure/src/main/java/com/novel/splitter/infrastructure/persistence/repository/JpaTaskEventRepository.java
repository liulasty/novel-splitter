package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaTaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaTaskEventRepository extends JpaRepository<JpaTaskEventEntity, Long> {
    List<JpaTaskEventEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
    List<JpaTaskEventEntity> findByTaskIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(String taskId, long sinceTimestamp);
}
