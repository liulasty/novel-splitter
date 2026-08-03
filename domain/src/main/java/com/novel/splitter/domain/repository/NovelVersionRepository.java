package com.novel.splitter.domain.repository;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;

import java.util.List;
import java.util.Optional;

/**
 * 小说版本（NovelVersion）仓库接口。
 */
public interface NovelVersionRepository {

    void save(NovelVersion version);

    Optional<NovelVersion> findById(String novelId, String versionTag);

    List<NovelVersion> findByNovelId(String novelId);

    void delete(String novelId, String versionTag);

    void deleteByNovelId(String novelId);

    /**
     * 查找超时停滞的版本：状态命中指定集合且 updatedAt 早于 beforeUpdatedAt。
     */
    List<NovelVersion> findStalled(List<VersionStatus> statuses, long beforeUpdatedAt);
}
