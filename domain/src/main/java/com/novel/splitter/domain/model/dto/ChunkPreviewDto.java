package com.novel.splitter.domain.model.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkPreviewDto {
    private int index;
    private String text;
    private int length;
    private String type;
}
