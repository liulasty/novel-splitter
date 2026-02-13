# Novel Splitter (小说切分与 RAG 预处理系统)

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.1-green)](https://spring.io/projects/spring-boot)

这是一个专门为 AI 时代打造的小说处理工具。

简单来说，它的作用是：**把一本几百万字的小说，自动“拆解”成 AI (ChatGPT, DeepSeek, Gemini 等) 能够理解和处理的小块数据（Scene），并提供高级的 RAG (检索增强生成) 能力。**

如果你想做一个“小说角色扮演 AI”或者“小说问答机器人”，那么这个项目就是为你准备的第一步基础设施。它负责把脏乱差的 TXT 文本，清洗、切分、整理成高质量的结构化数据，并确保 AI 回答时能获取到最准确、最连贯的上下文。

## ✨ 核心功能（人话版）

1.  **全自动下载**：内置了一个爬虫，只要你给个网址配置，它就能把几百章的小说自动爬下来，合并成一个 `novel.txt`。
2.  **智能切分**：它不是简单的“每500字切一刀”，而是能看懂章节（第几章）、能合并段落，尽量保证每个切分出来的片段（Scene）都是一个完整的小故事或场景。
3.  **高级 RAG 引擎**：不仅是简单的检索，还内置了独创的 **5 阶段上下文组装流水线**（重评分、去重、邻接合并、Token预算控制），显著提升 AI 回答的连贯性和准确性。
4.  **多模型支持**：开箱即用支持 **Gemini 1.5/2.0**, **DeepSeek V3**, **Coze (Bot)**, **Ollama** 等多种大模型。
5.  **可视化界面**：提供了一个网页界面，支持上传文件、查看切分结果、管理知识库版本、测试向量检索效果。

## 🚀 快速开始 (保姆级教程)

### 1. 环境准备
你需要安装以下软件：
- **JDK 21** (Java 21 是最低要求，因为用到了很多新特性)
- **Maven 3.8+** (用来编译项目)

### 2. 下载与编译
打开你的终端（CMD 或 PowerShell），执行以下命令：

```bash
# 1. 下载代码（假设你已经下载并解压了）
cd novel-splitter

# 2. 编译项目（这会下载依赖包，可能需要几分钟）
mvn clean package -DskipTests
```

如果看到 `BUILD SUCCESS`，恭喜你，编译成功了！

### 3. 配置 API Key (重要)
为了使用真实的大模型（如 Gemini 或 DeepSeek），你需要配置 API Key。

1. 在项目根目录下创建一个名为 `.env` 的文件（可以复制 `.env.example`）。
2. 填入你的 Key，例如：

```env
# Gemini
GEMINI_API_KEY=your_gemini_api_key

# DeepSeek
DEEPSEEK_API_KEY=your_deepseek_api_key

# Coze
COZE_API_KEY=your_coze_token
COZE_BOT_ID=your_bot_id
```

### 4. 运行 Web 界面
编译成功后，执行以下命令启动服务：

```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar
```

当看到 `Started NovelSplitApplication in ...` 字样时，打开浏览器访问：
👉 **http://localhost:8080/**

你可以在网页上：
- **上传**：上传本地的 TXT 小说文件。
- **切分**：点击“开始切分”，实时查看进度。
- **管理**：查看和管理已生成的知识库版本。
- **对话**：在 Chat 页面直接与你的小说进行对话（需配置好 API Key）。

### 5. 命令行运行 (极客模式)
如果你喜欢用命令行，也可以这样运行：

```bash
java -jar application/target/application-1.0.0-SNAPSHOT.jar --file="D:\books\斗破苍穹.txt" --version="v1"
```

---

## 🏗 项目架构与模块说明

这个项目被拆分成了 12 个模块，就像 12 个部门各司其职。

```
novel-splitter
├── application        # 【前台接待】 启动入口，Web界面和命令行接口都在这里。
├── domain             # 【通用语言】 定义了什么是"场景(Scene)"、"章节(Chapter)"，所有模块都听它的。
├── splitter           # 【切分车间】 核心业务，负责把长文本切成小块。里面有各种规则。
├── pipeline           # 【流水线】   指挥官，负责串联"读取->切分->校验->保存"这一整套流程。
├── repository         # 【仓库管理员】 负责把切好的数据存到硬盘上（JSON文件）。
├── validation         # 【质检员】   检查切出来的结果合不合格，比如是不是太短了。
├── infrastructure     # 【基建部】   提供读写文件、JSON处理、Dotenv 配置加载等底层工具。
├── novelDownloader    # 【采购部】   爬虫模块，负责从网上把小说抓取下来。
├── embedding          # 【翻译官】   (RAG) 负责把文字翻译成向量。支持本地 ONNX 模型和在线 API。
├── retrieval          # 【图书管理员】 (RAG) 负责根据你的问题，去仓库里找相关的片段。
├── context-assembler  # 【智能组装】 (RAG) 5 阶段流水线，负责将检索结果优化为高质量 Prompt。
└── llm-client         # 【外交官】   (RAG) 统一的大模型客户端，支持重试、熔断和多特定模型适配。
```

## 🛠 技术栈与依赖版本

本项目尽可能保持轻量级，没有引入复杂的数据库和中间件。

| 组件 | 版本 | 说明 |
| :--- | :--- | :--- |
| **Java** | **21** | 必须使用 JDK 21+ |
| **Spring Boot** | **3.2.1** | 核心框架 |
| **Maven** | 3.8+ | 构建工具 |
| **Lombok** | 1.18.30 | 简化代码（需IDE插件支持） |
| **Jackson** | 2.16.1 | JSON 处理 |
| **Dotenv Java** | 3.0.0 | 环境变量管理 |
| **Spring Retry** | 2.0.5 | 接口重试机制 |
| **ONNX Runtime** | 1.17.0 | 本地向量模型推理 |
| **Jsoup** | 1.17.2 | 爬虫网页解析 |

## 🧩 什么是 RAG？为什么需要它？

**RAG (Retrieval-Augmented Generation，检索增强生成)** 是目前让 AI 读懂私有数据的主流技术。

简单来说：
1.  **切分**: 把长小说切成无数个小片段（Scene）。
2.  **检索**: 当用户问“萧炎怎么认识药老的？”，系统先去搜相关的片段。
3.  **组装**: **(本项目特色)** 对检索结果进行重评分、去重、智能合并上下文，构建最佳 Prompt。
4.  **生成**: 把优化后的上下文 + 问题，一起发给 AI，AI 就能回答了。

**本项目专注于前 3 步的基础设施**。我们不做大模型本身，但我们为大模型准备最好的数据。

## ❓ 常见问题 (FAQ)

**Q: 我可以直接用它来跟小说对话吗？**
A: 可以！只要你在 `.env` 文件中配置了有效的 API Key (如 Gemini 或 DeepSeek)，并在 Web 界面选择相应的 Provider，就可以直接对话。系统默认也提供了 Mock 模式用于测试流程。

**Q: 为什么不直接用 LangChain？**
A: LangChain 很强大，但它是通用的。本项目是**专门为中文小说**优化的，我们在“章节识别”、“对话聚合”、“场景完整性”以及“RAG 上下文组装”上做了很多针对性的规则优化，比通用的切分器效果更好。

**Q: 切分后的数据存在哪里？**
A: 存在项目目录下的 `novel-storage` 文件夹里，格式是 JSON。你可以直接打开看，非常透明。

## 🤝 贡献与反馈
如果你发现某个小说切分效果不好，或者有新的想法，欢迎提交 Issue 或者 Pull Request！

---
*Happy Coding! 愿你的 AI 更懂小说。*
