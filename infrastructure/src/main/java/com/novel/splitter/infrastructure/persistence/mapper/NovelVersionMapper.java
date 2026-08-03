package com.novel.splitter.infrastructure.persistence.mapper;

import com.novel.splitter.domain.enums.VersionStatus;
import com.novel.splitter.domain.model.NovelVersion;
import com.novel.splitter.infrastructure.persistence.entity.JpaNovelVersionEntity;
import com.novel.splitter.infrastructure.persistence.entity.NovelVersionId;
import org.springframework.stereotype.Component;

/**
 * NovelVersion（domain）↔ JpaNovelVersionEntity（JPA）双向映射。
 * status 缺省 PENDING，createdAt/updatedAt 缺省 System.currentTimeMillis()。
 */
@Component
public class NovelVersionMapper {

    public JpaNovelVersionEntity toEntity(NovelVersion v) {
        if (v == null) return null;
        return JpaNovelVersionEntity.builder()
                .id(new NovelVersionId(v.getNovelId(), v.getVersionTag()))
                .splitStrategy(v.getSplitStrategy())
                .chunkSize(v.getChunkSize())
                .chunkOverlap(v.getChunkOverlap())
                .status(v.getStatus() != null ? v.getStatus() : VersionStatus.PENDING)
                .splitCursorChapterIndex(v.getSplitCursorChapterIndex())
                .splitCursorSceneSeq(v.getSplitCursorSceneSeq())
                .embedRunId(v.getEmbedRunId())
                .embedCursorSceneSeq(v.getEmbedCursorSceneSeq())
                .collectionName(v.getCollectionName())
                .activatedAt(v.getActivatedAt())
                .abandonedAt(v.getAbandonedAt())
                .createdAt(v.getCreatedAt() != 0 ? v.getCreatedAt() : System.currentTimeMillis())
                .updatedAt(v.getUpdatedAt() != 0 ? v.getUpdatedAt() : System.currentTimeMillis())
                .build();
    }

    public NovelVersion toDomain(JpaNovelVersionEntity e) {
        if (e == null) return null;
        NovelVersionId id = e.getId();
        return NovelVersion.builder()
                .novelId(id != null ? id.getNovelId() : null)
                .versionTag(id != null ? id.getVersionTag() : null)
                .splitStrategy(e.getSplitStrategy())
                .chunkSize(e.getChunkSize())
                .chunkOverlap(e.getChunkOverlap())
                .status(e.getStatus() != null ? e.getStatus() : VersionStatus.PENDING)
                .splitCursorChapterIndex(e.getSplitCursorChapterIndex())
                .splitCursorSceneSeq(e.getSplitCursorSceneSeq())
                .embedRunId(e.getEmbedRunId())
                .embedCursorSceneSeq(e.getEmbedCursorSceneSeq())
                .collectionName(e.getCollectionName())
                .activatedAt(e.getActivatedAt())
                .abandonedAt(e.getAbandonedAt())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
