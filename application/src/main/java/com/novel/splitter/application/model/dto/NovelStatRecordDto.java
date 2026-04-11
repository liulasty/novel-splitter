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
public class NovelStatRecordDto {
    /** novels 表主键，供 Chroma 过滤与 API 使用 */
    private String novelId;
    private String novelName;
    private List<String> versions;
    private long sceneCount;
    private long vectorCount;
    private String ingestTime;
    private String status;
}