package com.novel.splitter.repository.api;

import com.novel.splitter.domain.entity.JpaChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaChapterRepository extends JpaRepository<JpaChapterEntity, Long> {
    List<JpaChapterEntity> findByNovelIdOrderByIndexNumAsc(String novelId);
}
