package com.novel.splitter.infrastructure.persistence.repository;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelVersionEntity;
import com.novel.splitter.infrastructure.persistence.entity.NovelVersionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JpaNovelVersionRepository extends JpaRepository<JpaNovelVersionEntity, NovelVersionId> {

    List<JpaNovelVersionEntity> findById_NovelIdOrderById_VersionTagAsc(String novelId);

    void deleteById_NovelId(String novelId);

    /**
     * 跨小说扫描超时停滞版本：状态命中指定集合且 updatedAt 早于 beforeUpdatedAt。
     */
    @Query("select e from JpaNovelVersionEntity e where e.status in :statuses and e.updatedAt < :beforeUpdatedAt")
    List<JpaNovelVersionEntity> findStalled(
            @Param("statuses") Collection<VersionStatus> statuses,
            @Param("beforeUpdatedAt") long beforeUpdatedAt);
}
