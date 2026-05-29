package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaSystemConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JpaSystemConfigRepository extends JpaRepository<JpaSystemConfigEntity, Long> {
    Optional<JpaSystemConfigEntity> findByConfigKey(String configKey);
    List<JpaSystemConfigEntity> findByCategory(String category);
    List<JpaSystemConfigEntity> findByCategoryOrderByConfigKeyAsc(String category);
    void deleteByConfigKey(String configKey);
}
