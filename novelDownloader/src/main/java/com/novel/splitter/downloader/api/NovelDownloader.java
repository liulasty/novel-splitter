package com.novel.splitter.downloader.api;

import com.novel.splitter.downloader.model.DownloadChapter;
import java.util.List;

/**
 * 通用下载器接口
 */
public interface NovelDownloader {
    /**
     * 判断当前下载器是否支持该 URL
     */
    boolean canHandle(String url);

    /**
     * 执行下载
     * @param url 小说目录页 URL
     * @return 下载完成的章节列表
     */
    List<DownloadChapter> download(String url);
}
