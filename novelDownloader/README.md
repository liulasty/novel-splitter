# novelDownloader

## 模块概述
作为项目的数据抓取工具，负责从指定的小说网站抓取目录结构及章节正文内容，并将解析后的文本持久化到本地存储系统中，供后续流水线处理。

## 核心职责
- **网页内容抓取与解析**：利用 Jsoup 解析目标网页的 HTML 结构，精准抽取小说标题、章节目录列表（URL）、以及每一章的核心正文内容。
- **基于配置的动态适配**：通过 `application.yml` 中定义的 `downloader.sites` 节点，动态读取目标网站的域名、目录页选择器、内容选择器和翻页规则。
- **并发与下载策略**：提供统一的 `NovelDownloader` 接口和 `AbstractDownloader` 模板类，管理并发抓取线程（如 ThreadPool）及失败重试机制。
- **持久化管理**：将清洗后的无格式纯文本（或 Markdown）按既定规则交给 `infrastructure` 层的工具写入本地磁盘。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Jsoup, Commons Lang3

## 模块依赖
- 本模块依赖的内部子模块：`domain`, `infrastructure`
- 依赖本模块的内部子模块：`application`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `NovelDownloader` | 接口 | 暴露给上层的核心服务契约，定义了基于 URL 下载整本小说及监听下载进度的方法。 |
| `DownloaderFactory` | 工厂类 | 根据输入的 URL 域名，自动匹配并实例化合适的下载器实现类。 |
| `AbstractDownloader` | 抽象类 | 提供公共的下载控制流程（如 HTTP 客户端初始化、并发调度、限流重试）。 |
| `GeneralJsoupDownloader` | 实现类 | 基于 Jsoup 和 CSS 选择器（如 `#content`, `.chapter-list a`）实现通用的小说内容抽取。 |

## 使用示例
```java
// 从工厂获取对应的下载器
NovelDownloader downloader = DownloaderFactory.getDownloader("https://www.example.com/book/123");

// 启动下载，并保存至指定路径
Path savedPath = downloader.downloadNovel(
    "https://www.example.com/book/123",
    Path.of("/data/novel-storage/example_novel.txt"),
    progress -> System.out.println("当前下载进度：" + progress)
);
```

## 扩展点
- **扩展点 1**：对于反爬极其严格的站点，可继承 `AbstractDownloader` 实现基于 Playwright 或 Selenium 的无头浏览器下载器（Headless Browser Downloader）。
- **扩展点 2**：在 `DownloaderFactory` 注册新的特定网站（如起点、晋江）的深度定制解析器。

## 注意事项
- **注意 1**：由于目标站点结构可能随时变更，`application.yml` 中的 CSS 选择器规则需保持灵活且可热加载。
- **注意 2**：高并发抓取容易触发目标网站的 IP 封禁，必须在 `downloader` 配置中严格遵守 `thread-count` 和合理的 `timeout-ms`、延时策略。