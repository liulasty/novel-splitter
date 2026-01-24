# 自定义下载器开发指南

本模块支持通过扩展 `NovelDownloader` 接口来适配各种复杂的网站结构（如需要 AJAX 请求、加密解密、特殊 Header 等场景）。

## 1. 核心架构
系统使用**工厂模式**管理下载器：
- **Interface**: `NovelDownloader` (定义标准行为)
- **Base Class**: `AbstractDownloader` (提供多线程、重试、进度管理等通用能力)
- **Factory**: `DownloaderFactory` (根据 URL 路由到对应的下载器)

## 2. 开发步骤

### 步骤一：创建下载器类
建议继承 `AbstractDownloader`，这样你只需要关注核心的爬虫逻辑（获取目录、获取正文），而无需关心并发和异常处理。

在 `com.novel.splitter.downloader.impl` 包下新建类（例如 `XinghuoDownloader.java`）：

```java
package com.novel.splitter.downloader.impl;

import com.novel.splitter.downloader.core.AbstractDownloader;
import com.novel.splitter.downloader.model.DownloadChapter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import java.util.ArrayList;
import java.util.List;

public class XinghuoDownloader extends AbstractDownloader {

    // 目标域名
    private static final String TARGET_DOMAIN = "xinghuowxw.com";

    public XinghuoDownloader(int threadCount, int timeoutMs, int retryCount) {
        super(threadCount, timeoutMs, retryCount);
    }

    @Override
    public boolean canHandle(String url) {
        // 只要 URL 包含该域名，就由本下载器处理
        return url != null && url.contains(TARGET_DOMAIN);
    }

    @Override
    protected List<DownloadChapter> fetchCatalog(String url) {
        // 实现目录抓取逻辑
        // 示例：访问页面，解析 HTML 或 JSON
        List<DownloadChapter> chapters = new ArrayList<>();
        
        // 伪代码示例：
        // Document doc = Jsoup.connect(url).get();
        // Elements links = doc.select(".chapter-list a");
        // for (Element link : links) {
        //     chapters.add(DownloadChapter.builder()
        //         .index(index++)
        //         .title(link.text())
        //         .url(link.absUrl("href"))
        //         .build());
        // }
        
        return chapters;
    }

    @Override
    protected String fetchChapterContent(String url) {
        // 实现正文抓取逻辑
        // 注意：这里可能需要处理 AJAX 请求或解密
        
        // 伪代码示例：
        // Document doc = Jsoup.connect(url).header("Referer", ...).get();
        // return doc.select("#content").text();
        
        return "模拟正文内容...";
    }
}
```

### 步骤二：注册下载器
你需要将新写好的下载器注册到 `DownloaderFactory` 中。
修改 `application` 模块下的 `com.novel.splitter.application.service.DownloadService` 类：

```java
@PostConstruct
public void init() {
    // ... 原有的配置加载逻辑 ...

    // 注册你的自定义下载器
    AppConfig.DownloaderConfig config = appConfig.getDownloader();
    
    downloaderFactory.registerDownloader(new XinghuoDownloader(
        config.getThreadCount(),
        config.getTimeoutMs(),
        config.getRetryCount()
    ));
    
    log.info("Registered custom downloader: XinghuoDownloader");
}
```

### 步骤三：验证
1. 启动应用。
2. 调用下载接口（CLI 或 Web UI）。
3. 输入属于 `xinghuowxw.com` 的 URL。
4. 查看日志，确认日志中输出了 `Using downloader: XinghuoDownloader`。

## 3. 高级技巧

### 处理 AJAX 接口
如果网站是通过 AJAX 加载章节内容，`Jsoup.connect(url).get()` 可能拿不到数据。你需要模拟 API 请求：

```java
@Override
protected String fetchChapterContent(String url) {
    // 假设 url 是 https://site.com/book/1/2.html
    // 实际 API 是 https://site.com/api/chapter/2
    String apiUrl = convertToApiUrl(url); 
    
    String json = Jsoup.connect(apiUrl)
        .ignoreContentType(true)
        .execute()
        .body();
        
    // 解析 JSON (可以使用 Gson 或 Jackson)
    return parseJsonContent(json);
}
```

### 设置特殊请求头
某些网站有防盗链校验，可以在请求时添加 Header：

```java
Document doc = Jsoup.connect(url)
    .header("Referer", "https://www.target-site.com")
    .header("User-Agent", "Mozilla/5.0 ...")
    .cookie("auth", "token123")
    .get();
```

### 独立测试
在编写复杂逻辑时，建议在 `novelDownloader` 模块的 `src/test/java` 下编写单元测试，直接调用 `fetchCatalog` 方法进行验证，而不必每次都启动整个 Spring Boot 应用。
