package com.novel.splitter.repository.api;

import com.novel.splitter.repository.entity.JpaSceneEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaSceneRepository extends JpaRepository<JpaSceneEntity, Long> {
    
    // Custom query method for SubTask 2.2: "按 ID 列表查询的方法"
    List<JpaSceneEntity> findByIdIn(List<Long> ids);

    List<JpaSceneEntity> findByNovelNameAndVersion(String novelName, String version);

    List<JpaSceneEntity> findByNovelName(String novelName);

    void deleteByNovelNameAndVersion(String novelName, String version);

    void deleteByNovelName(String novelName);

    @Query("SELECT DISTINCT s.version FROM JpaSceneEntity s WHERE s.novelName = ?1")
    List<String> findDistinctVersionsByNovelName(String novelName);
}
