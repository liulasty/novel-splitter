package com.novel.splitter.infrastructure.persistence.entity;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 小说版本（NovelVersion）JPA 实体，复合主键 (novel_id, version_tag)。
 */
@Entity
@Table(name = "novel_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_novel_version_key", columnNames = {"novel_id", "version_tag"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JpaNovelVersionEntity {

    @EmbeddedId
    private NovelVersionId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_strategy", length = 32)
    private SplitStrategy splitStrategy;

    @Column(name = "chunk_size")
    private Integer chunkSize;

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private VersionStatus status;

    @Column(name = "split_cursor_chapter_index")
    private Integer splitCursorChapterIndex;

    @Column(name = "split_cursor_scene_seq")
    private Long splitCursorSceneSeq;

    @Column(name = "embed_run_id", length = 36)
    private String embedRunId;

    @Column(name = "embed_cursor_scene_seq")
    private Long embedCursorSceneSeq;

    @Column(name = "collection_name", length = 64)
    private String collectionName;

    @Column(name = "activated_at")
    private Long activatedAt;

    @Column(name = "abandoned_at")
    private Long abandonedAt;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;
}
