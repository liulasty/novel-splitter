package com.novel.splitter.application.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ChromaVersionDiagnosticDto {
    private long dbCount;
    private long chromaCount;
    private boolean consistent;
    private List<String> metadataKeys;
}
