package com.novel.splitter.application.service;

import com.novel.splitter.application.config.AppConfig;
import com.novel.splitter.downloader.api.NovelDownloader;
import com.novel.splitter.downloader.core.DownloaderFactory;
import com.novel.splitter.domain.model.downloader.DownloadChapter;
import com.novel.splitter.domain.model.downloader.SiteRule;
import com.novel.splitter.repository.api.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 下载服务
 * 负责匹配站点规则、执行下载任务并将结果保存到本地。
 */
@Slf4j
@Service
public class DownloadService {

    private final AppConfig appConfig;
    private final DownloaderFactory downloaderFactory;

    // 移除 NovelRepository 依赖，因为这里主要做文件写入，直接用 NIO
    // 如果必须用 NovelRepository，请确保 BeanConfig 中已定义
    
    public DownloadService(AppConfig appConfig) {
        this.appConfig = appConfig;
        
        // 初始化工厂
        AppConfig.DownloaderConfig dlConfig = appConfig.getDownloader();
        this.downloaderFactory = new DownloaderFactory(
                dlConfig.getThreadCount(),
                dlConfig.getTimeoutMs(),
                dlConfig.getRetryCount()
        );
    }

    @PostConstruct
    public void init() {
        // 注册配置文件中的站点规则
        if (appConfig.getDownloader().getSites() != null) {
            for (AppConfig.SiteConfig siteConfig : appConfig.getDownloader().getSites()) {
                SiteRule rule = SiteRule.builder()
                        .domain(siteConfig.getDomain())
                        .catalogUrl(siteConfig.getCatalogUrl())
                        .chapterListSelector(siteConfig.getChapterListSelector())
                        .contentSelector(siteConfig.getContentSelector())
                        .nextPageSelector(siteConfig.getNextPageSelector())
                        .build();
                
                downloaderFactory.registerGeneralRule(rule);
                log.info("Registered downloader for domain: {}", rule.getDomain());
            }
        }
        
        // TODO: 在这里可以注册自定义的下载器 (e.g., XinghuoDownloader)
        // downloaderFactory.registerDownloader(new XinghuoDownloader(...));
    }

    /**
     * 根据目录页 URL 下载整本小说
     *
     * @param url 目录页 URL
     * @param saveName   保存的文件名（不含后缀，默认保存为 txt）
     * @return 保存的绝对路径
     */
    public String downloadNovel(String url, String saveName) {
        // 1. 获取下载器
        NovelDownloader downloader = downloaderFactory.getDownloader(url);
        log.info("Using downloader: {}", downloader.getClass().getSimpleName());

        // 2. 执行下载
        List<DownloadChapter> chapters = downloader.download(url);

        // 3. 合并保存
        return saveMergedNovel(saveName, chapters);
    }

    private String saveMergedNovel(String name, List<DownloadChapter> chapters) {
        StringBuilder sb = new StringBuilder();
        for (DownloadChapter chapter : chapters) {
            sb.append(chapter.getTitle()).append("\n\n");
            if (chapter.isSuccess()) {
                sb.append(chapter.getContent()).append("\n\n");
            } else {
                sb.append("[Download Failed]\n\n");
            }
        }

        String rootPath = appConfig.getStorage().getRootPath();
        Path rawDir = Paths.get(rootPath, "raw");
        try {
            if (!Files.exists(rawDir)) {
                Files.createDirectories(rawDir);
            }
            String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            Path targetFile = rawDir.resolve(safeName + ".txt");
            
            Files.write(targetFile, sb.toString().getBytes(StandardCharsets.UTF_8));
            log.info("Saved novel to: {}", targetFile.toAbsolutePath());
            return targetFile.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save downloaded novel", e);
        }
    }
}
