package com.novel.splitter.domain.model.dto;

import lombok.Data;

@Data
public class IngestRequest {
    private String fileName; // e.g., "九阳帝尊-剑棕.txt"
    private int maxScenes = 0; // 0 means unlimited
    private String version; // Optional version tag
}
