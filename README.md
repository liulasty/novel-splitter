# Novel Splitter — 中文网文语义切分与 RAG 问答系统

> 把一部几十万字的中文网文，自动清洗、解析章节结构、再切成「语义场景」，向量化入库；
> 用户提问时，以 5 段式上下文组装把相关场景喂给大模型，实现**读完全本、精准作答**。

```
                入库：TXT 上传                           问答：用户提问
                      │                                       │
                      ▼                                       ▼
   ┌────────────────────────────────────┐      ┌────────────────────────────┐
   │         语义切分流水线（异步 MQ）      │      │         RAG 问答流水线        │
   │                                    │      │                            │
   │  清洗 → 章节识别 → 段落切分          │      │  向量检索 → 重排 → 去重       │
   │  → 场景组装 → 语义分析 → 向量化      │      │  → 合并 → Token预算 → 组装    │
   │                                    │      │            │               │
   └─────────────────┬──────────────────┘      └────────────┼───────────────┘
                     │                                      │
                     ▼                                      ▼
               ┌────────────────────────────────────────────────┐
               │   ChromaDB 向量库（场景 + 语义元数据，按版本隔离）  │
               └────────────────────────────────────────────────┘
```

## 核心能力

- **全自动流水线**：TXT 上传 → 清洗 → 章节识别 → 语义切分 → 场景组装 → 语义 enrich → 向量化 → ChromaDB
- **异步编排**：RabbitMQ 消息驱动，支持 1000+ 章长篇小说
- **零 API 成本的本地嵌入**：ONNX Runtime 推理 BGE-Small-ZH，无需联网
- **重排增强**：bge-reranker-base 对检索结果二次打分
- **5 段式 RAG 上下文组装**：重排 → 去重 → 合并 → Token 预算（硬约束）→ 最终组装
- **多 LLM 客户端**：DeepSeek / Gemini / Coze / Ollama 统一接入，内置重试与熔断
- **场景版本管理**：以 `(novelId, version)` 分区，多分块参数可共存
- **语义 enrich 门控**：语义标签全有或全无，保证 Chroma 中结构化键的一致
- **管理后台**：进度轮询、DLQ 重放、Chroma 诊断、向量预览一应俱全

## 快速开始

> 完整手册见 `CLAUDE.md`（开发指南）与 `DOCKER_COMPOSE_GUIDE.md`（Docker 操作手册）。

```bash
# 一键启动全部 5 个服务（PostgreSQL / RabbitMQ / ChromaDB / 后端 / 前端）
.\scripts\start-all.ps1
```

```bash
# 后端代码有改动时，先重新构建再启动
.\scripts\start-all.ps1 -Build
```

## 数据流水线（MQ 异步编排）

五个业务队列 + 各自死信队列（DLQ），阶段状态机 `PENDING → LOADING → SPLITTING → EMBEDDING → DONE/FAILED`：

```
 POST /api/novels/upload
        │  task = PENDING
        ▼
 novel.task.load ──► LoadWorker   （读 TXT → 清洗 → 章节识别）
        │
        ▼
 novel.task.split ─► SplitWorker （段落切分 → 场景组装）
        │
        ▼
 novel.task.enrich ► EnrichWorker（语义分析，产出结构化标签）
        │
        ▼
 novel.task.embed ─► EmbedWorker （ONNX 向量化 → ChromaDB）
        │
        ▼
 novel.task.cleanup► CleanupWorker（级联删除文件与向量）
```

## 架构：六边形 + DDD

**核心模式：六边形架构（Ports & Adapters）叠加 DDD 分层**，依赖严格由外向内，`application` 不依赖 `infrastructure`：

```
              ┌────────────────────────────────────────────┐
  入站适配器   │  interfaces   REST 控制器 / 启动入口          │
              ├────────────────────────────────────────────┤
  编排层       │  application  门面 / MQ Worker / 出站端口    │
              ├────────────────────────────────────────────┤
  处理引擎     │  batch-processing · text-processing        │
              │  validation · embedding · retrieval        │
              │  context-assembler · llm-client            │
              ├────────────────────────────────────────────┤
  领域层       │  domain      实体 / 仓储接口 / 策略契约       │
              ├────────────────────────────────────────────┤
  出站适配器   │  infrastructure  JPA 实现 / 文件 / JSON       │
              └────────────────────────────────────────────┘
```

| 模块 | 职责 |
|------|------|
| `domain/` | 核心实体、仓储接口、策略契约（六边形核心） |
| `application/` | 应用编排、MQ 消费者、出站端口（FileStorage/TaskQueue/TaskCache） |
| `interfaces/` | REST API、鉴权、全局异常，及其驱动/出站适配器 |
| `infrastructure/` | JPA 仓储实现、MapStruct 映射、文件/JSON 工具 |
| `batch-processing/` | 流水线 Use Case（Load → Split → Embed） |
| `text-processing/` | NLP：章节识别、段落切分、场景组装 |
| `validation/` | 语义段构建与对话/长度策略 |
| `embedding/` | ONNX 嵌入、重排、ChromaDB 存储 |
| `retrieval/` | RAG 检索、查询构建、回答策略分类 |
| `context-assembler/` | 5 段式上下文组装（重排→去重→合并→预算→组装） |
| `llm-client/` | DeepSeek/Gemini/Coze/Ollama 统一客户端 |
| `novel-splitter-web/` | React 19 + Vite + Zustand 管理前端 |

## 文档

- `CLAUDE.md` — 完整开发指南（含架构规则与设计模式说明）
- `DOCKER_COMPOSE_GUIDE.md` — Docker 操作手册
- `docs/UserManual.md` — 用户手册
- `docs/todo.md` — 已知问题与路线图
- `docs/decisions.md` — 关键架构决策记录

## License

[Internal Tool]
