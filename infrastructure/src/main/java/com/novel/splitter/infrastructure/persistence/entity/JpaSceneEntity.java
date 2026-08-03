package com.novel.splitter.infrastructure.persistence.entity;

import com.novel.splitter.domain.enums.EmbedStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scenes",
        uniqueConstraints = @UniqueConstraint(name = "uk_scene_version_seq",
                columnNames = {"novel_id", "version", "seq"}))
@Getter
@Setter
@ToString(exclude = {"novel", "chapter"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SQLRestriction("is_deleted = false")
public class JpaSceneEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "novel_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private JpaNovelEntity novel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chapter_id", foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private JpaChapterEntity chapter;

    @Column(name = "scene_id", nullable = false)
    private String sceneId;

    /**
     * Legacy column kept for DB backward compatibility.
     * <p>
     * P0 contract: scenes/chapters must not depend on human-facing novelName.
     * This value is written as a stable key (novelId) and is not used for queries.
     */
    @Column(name = "novel_name")
    private String legacyNovelName;

    @Column(name = "version", nullable = false)
    private String version;

    /** 全局场景序号（novelId,version 内单调），幂等续传的落点。 */
    @Column(name = "seq")
    private Long seq;

    /** 场景切分滑窗块大小；新数据必写，旧数据可能为 null */
    @Column(name = "chunk_size")
    private Integer chunkSize;

    /** 场景切分块重叠；新数据必写，旧数据可能为 null */
    @Column(name = "chunk_overlap")
    private Integer chunkOverlap;

    @Column(name = "chapter_title")
    private String chapterTitle;

    @Column(name = "chapter_index")
    private int chapterIndex;

    @Column(name = "start_paragraph_index")
    private int startParagraphIndex;

    @Column(name = "end_paragraph_index")
    private int endParagraphIndex;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "word_count")
    private int wordCount;

    @Column(name = "prefix_context", columnDefinition = "TEXT")
    private String prefixContext;

    @Column(name = "can_split")
    private boolean canSplit;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean isDeleted = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "embed_status")
    private EmbedStatus embedStatus;

    @Column(name = "embed_error", columnDefinition = "TEXT")
    private String embedError;

    @Column(name = "embed_run_id", length = 36)
    private String embedRunId;

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        JpaSceneEntity other = (JpaSceneEntity) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
