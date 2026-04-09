package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaChapterEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaChapterRepository extends JpaRepository<JpaChapterEntity, Long> {
    List<JpaChapterEntity> findByNovelIdOrderByIndexNumAsc(String novelId);

    @Modifying
    @Query("UPDATE JpaChapterEntity c SET c.isDeleted = true WHERE c.novel.id = ?1")
    void deleteByNovelId(String novelId);
}
