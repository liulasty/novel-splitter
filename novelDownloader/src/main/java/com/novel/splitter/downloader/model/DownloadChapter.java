package com.novel.splitter.downloader.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DownloadChapter {
    private int index;
    private String title;
    private String url;
    private String content;
    private boolean success;
}
