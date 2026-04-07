package com.novel.splitter.application.model.dto;

import com.novel.splitter.domain.model.SceneMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneDto {
    private String id;
    private String chapterTitle;
    private int chapterIndex;
    private int startParagraphIndex;
    private int endParagraphIndex;
    private String text;
    private int wordCount;
    private String prefixContext;
    private boolean canSplit;
    private SceneMetadata metadata;
    private Double score;
}