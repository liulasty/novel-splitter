package com.novel.splitter.application.model.dto;

public interface VectorPreviewRecordDto {
    Long getId();
    Integer getChapterIndex();
    String getType();
    Integer getTokenCount();
    String getTextContent();
}