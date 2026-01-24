# NovelDownloader 模块详细设计

## 1. 概述
NovelDownloader 是一个辅助模块，负责从网络获取小说文本，为 Splitter 模块提供原材料。它独立于核心切分逻辑，专注于爬虫、解析和清洗。

## 2. 核心功能
- **多源支持**：支持配置不同的网站规则（XPath/CSS Selectors）。
- **并发下载**：多线程下载章节内容。
- **断点续传**：记录下载进度，失败可重试。
- **内容清洗**：去除广告、多余空行、标准化格式。

## 3. 架构设计

### 3.1 核心组件
- **Downloader**: 调度器，管理下载任务队列。
- **SourceConfig**: 站点规则配置（JSON 格式）。
- **HtmlParser**: 解析 HTML 提取章节列表和正文。
- **ContentCleaner**: 文本清洗管道。

### 3.2 流程
1. **获取目录**：访问目录页，解析出所有章节 URL。
2. **过滤章节**：根据已下载记录，过滤出未下载章节。
3. **并发抓取**：启动线程池抓取正文页面。
4. **解析清洗**：提取正文，去除无关元素。
5. **保存/合并**：保存单章文件或合并为整本 TXT。

## 4. 模块结构 (基于 com.example.demo.util.novel)
```
novelDownloader
├── config
│   ├── DownloadConfig.java (线程数, 超时, 站点规则)
│   └── RequestHeaders.java
├── model
│   ├── Chapter.java
│   └── SiteRule.java (对应 JSON 配置)
├── service
│   ├── NovelDownloaderService.java
│   ├── ContentExtractionService.java (Jsoup 解析)
│   └── FileGenerationService.java
├── util
│   ├── NetworkUtil.java (HttpClient/OkHttp)
│   └── ContentCleaner.java
└── controller
    └── DownloadController.java (CLI 入口)
```

## 5. 配置示例 (SiteRule)
```json
{
  "domain": "www.example.com",
  "catalogUrl": "https://www.example.com/book/123",
  "chapterListSelector": ".chapter-list a",
  "contentSelector": "#content",
  "nextPageSelector": ".next-page" // 支持单章分页
}
```

## 6. 待办事项
- [ ] 引入 Jsoup 依赖。
- [ ] 实现通用的重试机制（指数退避）。
- [ ] 将硬编码的站点规则抽取为外部 JSON 配置文件。
- [ ] 统一 User-Agent 管理，防止被反爬。
