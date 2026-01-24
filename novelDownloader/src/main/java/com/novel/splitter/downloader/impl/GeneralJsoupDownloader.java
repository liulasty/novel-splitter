package com.novel.splitter.downloader.impl;

import com.novel.splitter.downloader.core.AbstractDownloader;
import com.novel.splitter.downloader.model.DownloadChapter;
import com.novel.splitter.downloader.model.SiteRule;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Jsoup 和 规则配置 的通用下载器
 */
@Slf4j
public class GeneralJsoupDownloader extends AbstractDownloader {

    private final SiteRule siteRule;

    public GeneralJsoupDownloader(SiteRule siteRule, int threadCount, int timeoutMs, int retryCount) {
        super(threadCount, timeoutMs, retryCount);
        this.siteRule = siteRule;
    }

    @Override
    public boolean canHandle(String url) {
        // 简单判断域名匹配
        return url.contains(siteRule.getDomain());
    }

    @Override
    protected List<DownloadChapter> fetchCatalog(String url) {
        List<DownloadChapter> chapters = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(timeoutMs)
                    .userAgent("Mozilla/5.0")
                    .get();

            Elements links = doc.select(siteRule.getChapterListSelector());
            int index = 1;
            for (Element link : links) {
                String href = link.absUrl("href");
                String title = link.text();
                if (!href.isEmpty()) {
                    chapters.add(DownloadChapter.builder()
                            .index(index++)
                            .title(title)
                            .url(href)
                            .build());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Catalog fetch failed", e);
        }
        return chapters;
    }

    @Override
    protected String fetchChapterContent(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .timeout(timeoutMs)
                    .userAgent("Mozilla/5.0")
                    .get();

            Element contentEl = doc.selectFirst(siteRule.getContentSelector());
            if (contentEl == null) {
                throw new IOException("Content element not found: " + siteRule.getContentSelector());
            }

            // 清洗
            doc.select("br").append("\\n");
            doc.select("p").prepend("\\n\\n");
            
            return contentEl.text().replace("\\n", "\n").trim();
        } catch (IOException e) {
            throw new RuntimeException("Content fetch failed", e);
        }
    }
}
