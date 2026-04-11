package com.novel.splitter.application.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SplitTaskPageDto {
    private List<SplitTaskDto> content;
    private int page;
    private int size;
    private long totalElements;
}
