package com.novel.splitter.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scenes")
@Data
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
    private String sceneId; // String ID from Scene

    @Column(name = "novel_name", nullable = false)
    private String novelName;

    @Column(name = "version", nullable = false)
    private String version;

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
}
