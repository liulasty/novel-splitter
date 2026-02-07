package com.novel.splitter.domain.model.downloader;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SiteRule {
    private String domain;
    private String catalogUrl;
    private String chapterListSelector;
    private String contentSelector;
    private String nextPageSelector;
}
