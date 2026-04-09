package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DB-first novel summary for listings.
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

