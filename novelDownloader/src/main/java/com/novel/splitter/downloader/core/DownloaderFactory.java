package com.novel.splitter.downloader.core;

import com.novel.splitter.downloader.api.NovelDownloader;
import com.novel.splitter.downloader.impl.GeneralJsoupDownloader;
import com.novel.splitter.downloader.model.SiteRule;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 下载器工厂
 * 负责根据 URL 路由到合适的下载器
 */
@Slf4j
public class DownloaderFactory {

    private final List<NovelDownloader> downloaders = new ArrayList<>();
    
    // 全局配置
    private final int threadCount;
    private final int timeoutMs;
    private final int retryCount;

    public DownloaderFactory(int threadCount, int timeoutMs, int retryCount) {
        this.threadCount = threadCount;
        this.timeoutMs = timeoutMs;
        this.retryCount = retryCount;
    }

    /**
     * 注册通用规则下载器
     */
    public void registerGeneralRule(SiteRule rule) {
        downloaders.add(new GeneralJsoupDownloader(rule, threadCount, timeoutMs, retryCount));
    }

    /**
     * 注册自定义下载器
     */
    public void registerDownloader(NovelDownloader downloader) {
        downloaders.add(downloader);
    }

    /**
     * 获取合适的下载器
     */
    public NovelDownloader getDownloader(String url) {
        for (NovelDownloader downloader : downloaders) {
            if (downloader.canHandle(url)) {
                return downloader;
            }
        }
        throw new IllegalArgumentException("No downloader found for URL: " + url);
    }
}
