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
    /** 该章在原文行范围内的段落行数（含空行占位） */
    private int paragraphCount;
    /** 所属卷标题（仅 VOLUME_CHAPTER 策略时非 null） */
    private String volumeTitle;
    /** 不含卷前缀的原始章标题 */
    private String originalTitle;
}