package com.novel.splitter.repository.api;

import com.novel.splitter.domain.entity.JpaTaskEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaTaskEventRepository extends JpaRepository<JpaTaskEventEntity, Long> {
    List<JpaTaskEventEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
}
