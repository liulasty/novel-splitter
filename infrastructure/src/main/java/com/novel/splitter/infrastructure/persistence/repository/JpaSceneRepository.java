package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.domain.enums.EmbedStatus;
import com.novel.splitter.domain.model.SceneCountByProfile;
import com.novel.splitter.infrastructure.persistence.entity.JpaSceneEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Stream;

@Repository
public interface JpaSceneRepository extends JpaRepository<JpaSceneEntity, Long>, JpaSpecificationExecutor<JpaSceneEntity> {
    
    @Query("SELECT s.id as id, s.chapterIndex as chapterIndex, 'SCENE' as type, s.wordCount as tokenCount, SUBSTRING(s.text, 1, 150) as textContent FROM JpaSceneEntity s")
    Page<SceneLightweightProjection> findLightweightScenes(Pageable pageable);

    // Custom query method for SubTask 2.2: "按 ID 列表查询的方法"
    List<JpaSceneEntity> findByIdIn(List<Long> ids);

    List<JpaSceneEntity> findBySceneIdIn(List<String> sceneIds);

    Page<JpaSceneEntity> findByNovelId(String novelId, Pageable pageable);

    @EntityGraph(attributePaths = {"novel", "chapter"})
    List<JpaSceneEntity> findByNovelId(String novelId);

    @EntityGraph(attributePaths = {"novel", "chapter"})
    List<JpaSceneEntity> findByNovelIdAndVersion(String novelId, String version);

    @EntityGraph(attributePaths = {"novel", "chapter"})
    List<JpaSceneEntity> findByNovelIdAndVersionAndChunkSizeAndChunkOverlap(
            String novelId, String version, Integer chunkSize, Integer chunkOverlap);

    @Query("SELECT s.id FROM JpaSceneEntity s WHERE s.novel.id = :nid AND s.version = :ver "
            + "AND s.chunkSize = :cs AND s.chunkOverlap = :co AND s.isDeleted = false ORDER BY s.id")
    List<Long> findPersistenceIdsByProfile(
            @Param("nid") String novelId,
            @Param("ver") String version,
            @Param("cs") int chunkSize,
            @Param("co") int chunkOverlap);

    @EntityGraph(attributePaths = {"novel", "chapter"})
    Page<JpaSceneEntity> findByNovelIdAndVersionAndChunkSizeAndChunkOverlap(
            String novelId, String version, Integer chunkSize, Integer chunkOverlap, Pageable pageable);

    Stream<JpaSceneEntity> streamAllByNovelIdAndVersionAndChunkSizeAndChunkOverlap(
            String novelId, String version, Integer chunkSize, Integer chunkOverlap);

    @Query("SELECT s FROM JpaSceneEntity s")
    Stream<JpaSceneEntity> streamAll();

    long countByNovelIdAndVersionAndChunkSizeAndChunkOverlap(
            String novelId, String version, Integer chunkSize, Integer chunkOverlap);

    @Query("SELECT COUNT(s) FROM JpaSceneEntity s WHERE s.novel.id = ?1 AND s.version = ?2")
    long countByNovelIdAndVersionAllChunks(String novelId, String version);

    @EntityGraph(attributePaths = {"novel", "chapter"})
    Page<JpaSceneEntity> findByNovelIdAndChapterId(String novelId, Long chapterId, Pageable pageable);

    @Modifying
    @Query("UPDATE JpaSceneEntity s SET s.isDeleted = true WHERE s.novel.id = ?1 AND s.version = ?2 "
            + "AND s.chunkSize = ?3 AND s.chunkOverlap = ?4")
    void deleteByNovelIdAndVersionAndChunkSizeAndChunkOverlap(
            String novelId, String version, Integer chunkSize, Integer chunkOverlap);

    @Modifying
    @Query("UPDATE JpaSceneEntity s SET s.isDeleted = true WHERE s.novel.id = ?1")
    void deleteByNovelId(String novelId);

    @Query("SELECT DISTINCT s.version, s.chunkSize, s.chunkOverlap FROM JpaSceneEntity s WHERE s.novel.id = ?1")
    List<Object[]> findDistinctProfilesByNovelId(String novelId);

    @Query("SELECT new com.novel.splitter.domain.model.SceneCountByProfile("
            + "s.novel.id, s.version, s.chunkSize, s.chunkOverlap, COUNT(s)) FROM JpaSceneEntity s "
            + "GROUP BY s.novel.id, s.version, s.chunkSize, s.chunkOverlap")
    List<SceneCountByProfile> countScenesByNovelVersionAndChunk();

    interface SceneLightweightProjection {
        Long getId();
        Integer getChapterIndex();
        String getType();
        Integer getTokenCount();
        String getTextContent();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JpaSceneEntity s SET s.embedStatus = :st, s.embedError = null, s.embedRunId = :rid "
            + "WHERE s.novel.id = :nid AND s.version = :ver AND s.chunkSize = :cs AND s.chunkOverlap = :co AND s.isDeleted = false")
    int resetEmbedStateForRun(
            @Param("nid") String novelId,
            @Param("ver") String version,
            @Param("cs") int chunkSize,
            @Param("co") int chunkOverlap,
            @Param("rid") String embedRunId,
            @Param("st") EmbedStatus embedStatus);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JpaSceneEntity s SET s.embedStatus = :st, s.embedError = :err "
            + "WHERE s.id = :id AND s.embedRunId = :rid AND s.isDeleted = false")
    int updateEmbedOutcome(
            @Param("id") Long persistenceId,
            @Param("rid") String embedRunId,
            @Param("st") EmbedStatus status,
            @Param("err") String embedError);

    @Query("SELECT COUNT(s) FROM JpaSceneEntity s WHERE s.novel.id = :nid AND s.version = :ver "
            + "AND s.chunkSize = :cs AND s.chunkOverlap = :co AND s.embedRunId = :rid AND s.embedStatus = :st "
            + "AND s.isDeleted = false")
    long countByProfileRunAndStatus(
            @Param("nid") String novelId,
            @Param("ver") String version,
            @Param("cs") int chunkSize,
            @Param("co") int chunkOverlap,
            @Param("rid") String embedRunId,
            @Param("st") EmbedStatus status);

    @Query("SELECT s.id FROM JpaSceneEntity s WHERE s.novel.id = :nid AND s.version = :ver "
            + "AND s.chunkSize = :cs AND s.chunkOverlap = :co AND s.embedRunId = :rid "
            + "AND s.embedStatus IN :stats AND s.isDeleted = false")
    List<Long> findIdsByProfileAndRunAndEmbedStatusIn(
            @Param("nid") String novelId,
            @Param("ver") String version,
            @Param("cs") int chunkSize,
            @Param("co") int chunkOverlap,
            @Param("rid") String embedRunId,
            @Param("stats") List<EmbedStatus> stats);

    @Query("SELECT s.chunkSize, s.chunkOverlap FROM JpaSceneEntity s WHERE s.novel.id = :nid AND s.version = :ver "
            + "AND s.embedRunId = :rid AND s.isDeleted = false")
    List<Object[]> findChunkRowsByEmbedRun(
            @Param("nid") String novelId,
            @Param("ver") String version,
            @Param("rid") String embedRunId);
}
