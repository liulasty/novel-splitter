package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.infrastructure.persistence.entity.JpaSceneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

@Repository
public interface JpaSceneRepository extends JpaRepository<JpaSceneEntity, Long>, JpaSpecificationExecutor<JpaSceneEntity> {
    
    @Query("SELECT s.id as id, s.chapterIndex as chapterIndex, 'SCENE' as type, s.wordCount as tokenCount, SUBSTRING(s.text, 1, 150) as textContent FROM JpaSceneEntity s")
    Page<com.novel.splitter.domain.model.dto.VectorPreviewRecordDto> findLightweightScenes(Pageable pageable);

    // Custom query method for SubTask 2.2: "按 ID 列表查询的方法"
    List<JpaSceneEntity> findByIdIn(List<Long> ids);

    List<JpaSceneEntity> findBySceneIdIn(List<String> sceneIds);

    List<JpaSceneEntity> findByNovelNameAndVersion(String novelName, String version);
    
    List<JpaSceneEntity> findByNovelIdAndEmbedStatus(String novelId, String embedStatus);

    Page<JpaSceneEntity> findByNovelId(String novelId, Pageable pageable);

    Stream<JpaSceneEntity> streamAllByNovelNameAndVersion(String novelName, String version);

    @Query("SELECT s FROM JpaSceneEntity s")
    Stream<JpaSceneEntity> streamAll();

    long countByNovelNameAndVersion(String novelName, String version);

    List<JpaSceneEntity> findByNovelName(String novelName);

    @Modifying
    @Query("UPDATE JpaSceneEntity s SET s.isDeleted = true WHERE s.novelName = ?1 AND s.version = ?2")
    void deleteByNovelNameAndVersion(String novelName, String version);

    @Modifying
    @Query("UPDATE JpaSceneEntity s SET s.isDeleted = true WHERE s.novelName = ?1")
    void deleteByNovelName(String novelName);

    @Query("SELECT DISTINCT s.version FROM JpaSceneEntity s WHERE s.novelName = ?1")
    List<String> findDistinctVersionsByNovelName(String novelName);

    @Query("SELECT s.novelName, s.version, COUNT(s) FROM JpaSceneEntity s GROUP BY s.novelName, s.version")
    List<Object[]> countScenesByNovelAndVersion();
}
