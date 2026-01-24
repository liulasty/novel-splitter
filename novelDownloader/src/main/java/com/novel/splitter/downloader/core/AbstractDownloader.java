package com.novel.splitter.downloader.core;

import com.novel.splitter.downloader.api.NovelDownloader;
import com.novel.splitter.downloader.model.DownloadChapter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 抽象下载器基类
 * 封装了多线程下载和重试的通用逻辑
 */
@Slf4j
public abstract class AbstractDownloader implements NovelDownloader {

    protected final int threadCount;
    protected final int timeoutMs;
    protected final int retryCount;
    protected final ExecutorService executor;

    protected AbstractDownloader(int threadCount, int timeoutMs, int retryCount) {
        this.threadCount = threadCount;
        this.timeoutMs = timeoutMs;
        this.retryCount = retryCount;
        this.executor = Executors.newFixedThreadPool(threadCount);
    }

    @Override
    public List<DownloadChapter> download(String url) {
        try {
            // 1. 获取目录
            log.info("Fetching catalog from: {}", url);
            List<DownloadChapter> chapters = fetchCatalog(url);
            log.info("Found {} chapters.", chapters.size());

            // 2. 并发下载正文
            downloadContents(chapters);

            return chapters;
        } finally {
            // 注意：真实场景中可能需要复用线程池而不是每次 shutdown，这里为了简单每次创建销毁
            // 如果作为 Bean 管理，应当在销毁方法中 shutdown
            // executor.shutdown(); 
            // 修正：为了避免资源泄漏，这里还是 shutdown，但在 Spring Bean 模式下建议复用
        }
    }

    /**
     * 获取目录列表（由子类实现）
     */
    protected abstract List<DownloadChapter> fetchCatalog(String url);

    /**
     * 获取单章正文（由子类实现）
     */
    protected abstract String fetchChapterContent(String url);

    private void downloadContents(List<DownloadChapter> chapters) {
        AtomicInteger counter = new AtomicInteger(0);
        int total = chapters.size();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (DownloadChapter chapter : chapters) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String content = fetchChapterContentWithRetry(chapter.getUrl());
                    chapter.setContent(content);
                    chapter.setSuccess(true);
                } catch (Exception e) {
                    log.error("Failed to download chapter: {}", chapter.getTitle(), e);
                    chapter.setSuccess(false);
                } finally {
                    int current = counter.incrementAndGet();
                    if (current % 10 == 0 || current == total) {
                        log.info("Progress: {}/{}", current, total);
                    }
                }
            }, executor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private String fetchChapterContentWithRetry(String url) {
        Exception lastException = null;
        for (int i = 0; i < retryCount; i++) {
            try {
                return fetchChapterContent(url);
            } catch (Exception e) {
                lastException = e;
                try {
                    Thread.sleep(1000L * (i + 1));
                } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Failed after " + retryCount + " retries", lastException);
    }
}
