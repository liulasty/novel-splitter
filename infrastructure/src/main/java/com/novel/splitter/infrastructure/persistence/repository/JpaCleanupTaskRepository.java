package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaCleanupTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaCleanupTaskRepository extends JpaRepository<JpaCleanupTaskEntity, Long> {
    List<JpaCleanupTaskEntity> findByStatus(String status);
}