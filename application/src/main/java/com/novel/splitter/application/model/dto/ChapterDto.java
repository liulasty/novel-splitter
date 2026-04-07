package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChapterDto {
    private Long id;
    private String novelId;
    private int index;
    private String title;
    private int startParagraphIndex;
    private int endParagraphIndex;
    private int wordCount;
}