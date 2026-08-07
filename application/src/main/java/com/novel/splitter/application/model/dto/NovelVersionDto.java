package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说版本（NovelVersion）对外展示 DTO。
 * <p>
 * {@code splitStrategy} / {@code status} 以字符串形式暴露便于前端渲染；
 * {@code active} 由 {@code novel.activeVersionTag} 判定当前是否为检索所用活跃版本。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelVersionDto {

    private String novelId;
    private String versionTag;
    private String splitStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String status;

    /** 切分游标：已完成的章节索引 */
    private Integer splitCursorChapterIndex;
    /** 切分游标：已完成的场景序号 */
    private Long splitCursorSceneSeq;

    /** 向量化运行标识 */
    private String embedRunId;
    /** 向量化游标：已完成的场景序号 */
    private Long embedCursorSceneSeq;

    /** ChromaDB 集合名 */
    private String collectionName;
    private Long activatedAt;
    private long createdAt;
    private long updatedAt;

    /** 是否当前活跃版本（novel.activeVersionTag 相等） */
    private boolean active;

    /** 语义抽取完成度（0-100，%场景含非空语义字段）；无场景时为 null */
    private Integer enrichProgress;

    /** 语义抽取是否全部完成（有场景且 enrichProgress == 100） */
    private boolean enrichComplete;
}
