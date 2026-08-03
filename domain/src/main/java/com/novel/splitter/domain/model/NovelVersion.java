package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.SplitStrategy;
import com.novel.splitter.domain.enums.VersionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小说版本（NovelVersion）聚合
 * <p>
 * 同一部小说在特定切分参数（策略/窗口/重叠）下的切分与向量化运行单元。
 * 状态机：PENDING → SPLITTING → SPLIT_DONE → EMBEDDING → EMBED_DONE → ACTIVE；
 * 任意非终态可 FAILED/ABANDONED，FAILED 可重新进入 SPLITTING/EMBEDDING。
 * 终态为 ACTIVE 与 ABANDONED。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelVersion {

    private String novelId;
    private String versionTag;
    private SplitStrategy splitStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private VersionStatus status;

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
    private Long abandonedAt;

    private long createdAt;
    private long updatedAt;

    /**
     * 启动场景切分。
     * <p>仅终态（ACTIVE/ABANDONED）拒绝；SPLITTING 幂等返回，其余状态置 SPLITTING。</p>
     */
    public void startSplit() {
        if (this.status == VersionStatus.ACTIVE || this.status == VersionStatus.ABANDONED) {
            throw new IllegalStateException("终态版本不支持重新切分: " + this.status);
        }
        if (this.status == VersionStatus.SPLITTING) {
            return;
        }
        this.status = VersionStatus.SPLITTING;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 完成场景切分：仅 SPLITTING → SPLIT_DONE。
     */
    public void completeSplit() {
        if (this.status != VersionStatus.SPLITTING) {
            throw new IllegalStateException("只有切分中的版本才能完成切分: " + this.status);
        }
        this.status = VersionStatus.SPLIT_DONE;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 启动向量化：SPLIT_DONE 或 FAILED → EMBEDDING，其余抛异常。
     */
    public void startEmbed() {
        if (this.status != VersionStatus.SPLIT_DONE && this.status != VersionStatus.FAILED) {
            throw new IllegalStateException("当前状态不支持启动向量化: " + this.status);
        }
        this.status = VersionStatus.EMBEDDING;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 完成向量化：仅 EMBEDDING → EMBED_DONE。
     */
    public void completeEmbed() {
        if (this.status != VersionStatus.EMBEDDING) {
            throw new IllegalStateException("只有向量化中的版本才能完成向量化: " + this.status);
        }
        this.status = VersionStatus.EMBED_DONE;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 激活版本：仅 EMBED_DONE → ACTIVE，并记录激活时间。
     */
    public void activate() {
        if (this.status != VersionStatus.EMBED_DONE) {
            throw new IllegalStateException("只有向量化完成的版本才能激活: " + this.status);
        }
        this.status = VersionStatus.ACTIVE;
        this.activatedAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 标记失败：任意状态 → FAILED。
     */
    public void fail() {
        this.status = VersionStatus.FAILED;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 废弃版本：任意状态 → ABANDONED，并记录废弃时间。
     */
    public void abandon() {
        this.status = VersionStatus.ABANDONED;
        this.abandonedAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 推进切分游标（同时刷新心跳，避免超时回收误杀进行中的长任务）。
     */
    public void advanceSplitCursor(int chapterIndex, long sceneSeq) {
        this.splitCursorChapterIndex = chapterIndex;
        this.splitCursorSceneSeq = sceneSeq;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * 是否超时停滞：仅 SPLITTING/EMBEDDING 状态且 (now - updatedAt) &gt; thresholdMs。
     */
    public boolean isStalled(long now, long thresholdMs) {
        if (this.status != VersionStatus.SPLITTING && this.status != VersionStatus.EMBEDDING) {
            return false;
        }
        return (now - this.updatedAt) > thresholdMs;
    }
}
