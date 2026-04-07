package com.novel.splitter.domain.model;

import com.novel.splitter.domain.enums.NovelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * 小说 (Novel) 聚合根
 * <p>
 * 代表一本完整的小说，包含元数据、章节列表和所有原始段落。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Jacksonized
public class Novel {
    private String id;
    private String title;
    private String author;
    private String description;
    private String coverUrl;
    private String filePath;
    private NovelStatus status;
    private long createdAt;
    private long updatedAt;
    private boolean isDeleted;

    /**
     * 章节列表
     */
    private List<Chapter> chapters;

    /**
     * 原始段落列表 (全局)
     */
    private List<RawParagraph> paragraphs;

    // 领域行为
    public void completeTask() {
        this.status = NovelStatus.COMPLETED;
        this.updatedAt = System.currentTimeMillis();
    }

    public void failTask() {
        this.status = NovelStatus.FAILED;
        this.updatedAt = System.currentTimeMillis();
    }

    public void updateStatus(NovelStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = System.currentTimeMillis();
    }
}
