# Novel Splitter (小说切分与 RAG 预处理系统)

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)

一个工程化、模块化的小说文本切分与结构化系统。旨在将任意长度的 TXT 小说转换为结构清晰、语义完整的 Scene（场景）数据，并提供 RAG（检索增强生成）所需的检索与上下文组装能力。

## ✨ 核心特性

- **确定性切分**：基于规则而非概率，保证结果的稳定性和可复现性。
- **多层级结构**：支持 Paragraph -> SemanticSegment -> Scene -> Chapter 的多层级抽象。
- **RAG 就绪**：内置 Embedding 接口、向量检索 (Retrieval) 和上下文组装 (Context Assembler) 模块。
- **模块化设计**：领域模型(Domain)、切分引擎(Splitter)、流水线(Pipeline)、检索(Retrieval) 完全解耦。
- **内置爬虫**：提供可扩展的小说下载器框架，支持自定义站点规则。
- **可视化测试**：提供 Web UI 方便快速验证切分效果。

## 🏗 架构概览

```
novel-splitter
├── domain            # 核心业务模型 (Scene, Chapter, ContextBlock)
├── splitter          # 切分规则引擎 (ParagraphSplitter, SceneAssembler)
├── pipeline          # 任务流水线编排
├── repository        # 本地文件存储实现
├── validation        # 数据质量校验
├── infrastructure    # 基础设施 (IO, JSON)
├── novelDownloader   # 爬虫模块 (DownloaderFactory, Jsoup)
├── embedding         # 向量化接口与 Mock 实现
├── retrieval         # 向量检索服务 (VectorRetrievalService)
├── context-assembler # LLM 上下文组装器
├── llm-client        # LLM 客户端接口与 Mock 实现
└── application       # Spring Boot 启动入口 & REST API
```

## 🚀 快速开始

### 1. 编译
```bash
mvn clean package -DskipTests
```

### 2. 运行 Web 界面
启动应用：
```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar
```
访问：http://localhost:8080/

### 3. 命令行运行
```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar --file="novel.txt" --version="v1"
```

## 📖 文档
- [详细使用说明 (USAGE.md)](application/USAGE.md)
- [爬虫开发指南 (DEVELOPER_GUIDE.md)](novelDownloader/DEVELOPER_GUIDE.md)
- [架构设计文档](docs/design/)
- [实施计划](docs/plan/implementation_plan.md)

## 🛠 配置
修改 `application/src/main/resources/application.yml` 可调整切分粒度、爬虫规则及 RAG 配置。

```yaml
splitter:
  rule:
    target-length: 1200 # 目标场景字数

rag:
  max-token-limit: 3000
  retrieval:
    top-k: 5

downloader:
  sites:
    - domain: "www.example.com"
      catalog-url: "..."
```

## 🤝 贡献
欢迎提交 Issue 或 PR 改进切分规则。
