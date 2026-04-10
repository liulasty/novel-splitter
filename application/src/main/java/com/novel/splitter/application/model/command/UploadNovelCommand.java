package com.novel.splitter.application.model.command;

import lombok.Builder;
import lombok.Data;
import java.io.InputStream;

@Data
@Builder
public class UploadNovelCommand {
    private String title;
    private String author;
    private String description;
    private String originalFilename;
    private InputStream inputStream;
    private long size;
}