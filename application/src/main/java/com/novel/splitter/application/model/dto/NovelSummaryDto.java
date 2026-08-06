package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 面向列表展示的小说摘要（数据来自数据库，而非文件名推断）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelSummaryDto {
    private String novelId;
    private String title;
    private String author;
    private String status;
    private String filePath;
    private long createdAt;
    private long updatedAt;
}

