# 一、项目目标与边界

## 1.1 项目目标

构建一个**完全离线、本地运行**的小说文本切分与结构化系统，用于：

- 将单部 5MB～百万字级中文小说
- 稳定、可复现地切分为 **Scene（语义场景单元）**
- 为后续：向量化、检索、问答、分析提供**长期可靠的结构化语料**
- **RAG 预处理**：提供标准的上下文组装与检索能力，衔接 LLM 应用。

> 本项目**主要解决“切分与结构化”及“检索准备”问题**，不包含：
>
> - 生产级 LLM 推理（仅提供 Mock 客户端与接口）
> - 复杂的问答编排逻辑
>
> 这是整个系统中**最底层、最不可返工的一层**。

------

## 1.2 设计原则（非常重要）

1. **工程优先，不赌模型能力**
2. **切分结果必须可审计、可回滚、可重建**
3. **不引入数据库、不引入外部服务（除 LLM API 外）**
4. **所有中间产物都是“稳定文件”**
5. **RAG 模块化**：检索、向量化、上下文组装各司其职。

------

# 二、总体架构视图（逻辑层级）

```
novel-splitter
│
├─ application        ← Spring Boot 启动 & 任务编排
│
├─ domain             ← 核心业务模型（Scene / Chapter / Segment / ContextBlock）
│
├─ splitter           ← 切分规则引擎（纯业务核心）
│
├─ repository         ← 本地文件存储抽象
│
├─ pipeline           ← 多阶段处理流水线
│
├─ validation         ← 切分质量校验
│
├─ infrastructure     ← JSON / 文件 / 配置实现
│
├─ novelDownloader    ← 小说文件下载 (Factory + Jsoup)
│
├─ embedding          ← 向量化服务 (EmbeddingService + Mock)
│
├─ retrieval          ← 向量检索服务 (RetrievalService)
│
├─ context-assembler  ← LLM 上下文组装 (ContextAssembler)
│
└─ llm-client         ← LLM 客户端抽象 (LlmClient + Mock)
```

> **注意**：
>
> - 没有 DAO / Entity / Mapper
> - Repository 不是数据库概念，而是“文件产物管理器”
> - RAG 相关模块（retrieval, embedding, context-assembler）遵循 Clean Architecture

------

# 三、模块拆解（逐层说明）

## 3.1 application 层（Spring Boot）

### 职责

- 提供 CLI / REST 触发入口
- 串联切分 Pipeline
- 集成 RAG 服务
- 不包含任何切分规则

### 示例结构

```
application
├─ NovelSplitApplication.java
├─ SplitCommandRunner.java
└─ SplitController.java
```

------

## 3.2 domain 层（纯模型）

### 核心对象

```
domain
├─ RawParagraph
├─ SemanticSegment
├─ Scene
├─ Chapter
├─ SceneMetadata
└─ ContextBlock (RAG 用)
```

### Scene 示例（核心对象）

```java
public class Scene {
    String sceneId;
    String chapterTitle;
    int chapterIndex;
    int startParagraph;
    int endParagraph;
    String text;
    SceneMetadata metadata; // 包含 RAG 所需元数据
}
```

------

## 3.3 splitter 层（最关键）

### 职责

实现**可解释、规则驱动**的文本切分逻辑。

### 切分阶段划分

```
Raw Text
  ↓
物理段落切分
  ↓
语义段落合并（时间 / 视角 / 场景）
  ↓
Scene 构建
```

### 规则特点

- 全部是 **deterministic（确定性）**
- 不调用模型
- 同一输入 → 永远同一输出

------

## 3.4 pipeline 层（流水线编排）

### 职责

把 splitter 的“原子能力”串成**稳定流程**。

```
pipeline
├─ SplitPipeline
├─ PipelineContext
└─ PipelineStage (Load, Split, Validation, Save)
```

------

## 3.5 repository 层（本地文件存储）

### 关键认知

> Repository ≠ Database
>
> Repository = **“版本化文件产物管理”**

### 目录结构（真实落地）

```
novel-storage/
├─ raw/
│   └─ novel.txt
├─ scene/
│   ├─ v1-rule-basic/
│   │   └─ scenes.json
│   └─ v2-rule-enhanced/
│       └─ scenes.json
└─ meta/
    └─ split-report.json
```

------

## 3.6 validation 层（质量控制）

### 职责

保证切分结果**不是“看天吃饭”**。

### 校验项

- Scene 字数分布是否异常
- 是否出现极短 / 极长 Scene
- 是否跨章节异常
- Scene 连续性是否断裂

------

## 3.7 novelDownloader 层（爬虫）

### 职责

提供可扩展的小说下载能力，支持多站点规则配置。

### 架构

- **DownloaderFactory**: 根据配置创建下载器实例。
- **AbstractDownloader**: 提供通用模板方法（多线程、重试、文件合并）。
- **GeneralJsoupDownloader**: 基于 Jsoup 和规则配置的通用实现。

### 配置示例

```yaml
downloader:
  sites:
    - domain: "www.example.com"
      catalog-url: "..."
      rule:
        chapter-list-selector: "..."
        content-selector: "..."
```

------

## 3.8 RAG 相关模块 (Embedding, Retrieval, Context-Assembler, LLM-Client)

为了支持下游的 RAG 应用，系统扩展了以下模块：

### Embedding
- **职责**: 将文本转换为向量。
- **实现**: 定义 `EmbeddingService` 接口，目前提供 `MockEmbeddingService` (固定维度随机向量) 用于测试。

### Retrieval
- **职责**: 基于语义相似度检索相关 Scene。
- **实现**: `VectorRetrievalService`，结合 Embedding 和 Repository 实现内存/向量库检索。

### Context-Assembler
- **职责**: 将检索到的 Scene 组装成符合 LLM 输入窗口限制的 Prompt 上下文。
- **特性**: 支持 Token 限制、截断策略、格式化输出。

### LLM-Client
- **职责**: 与大模型交互的抽象接口。
- **实现**: `LlmClient` 接口，目前提供 `MockLlmClient` 用于离线验证。

------

# 四、关键业务流程

## 4.1 切分完整流程

1. **Download**: 下载小说文本 (novelDownloader)
2. **Load**: 读取 raw 小说文本
3. **Split**: 按换行切为物理段落 -> 识别章节 -> 合并语义段 -> 聚合 Scene
4. **Validate**: 校验 Scene 合理性
5. **Save**: 写入 JSON（版本化）

## 4.2 RAG 准备流程 (未来扩展)

1. **Embedding**: 读取 Scene JSON -> 调用 EmbeddingService -> 生成向量
2. **Indexing**: 存入向量库 (VectorStore)

------

# 五、核心依赖（极简）

```groovy
dependencies {
  implementation 'org.springframework.boot:spring-boot-starter'
  implementation 'com.fasterxml.jackson.core:jackson-databind'
  implementation 'org.apache.commons:commons-lang3'
  implementation 'org.jsoup:jsoup' // 爬虫
}
```

> 没有：
>
> - 数据库 (使用文件系统)
> - ORM
> - 消息队列
> - Python 依赖 (纯 Java 实现)
