package com.novel.splitter.domain.model.dto;

public interface VectorPreviewRecordDto {
    Long getId();
    Integer getChapterIndex();
    String getType();
    Integer getTokenCount();
    String getTextContent();
}