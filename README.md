# Novel Splitter — 中文网文语义切分与 RAG 问答系统

将中文网络小说转换为 RAG-ready 语义场景，支持上传、清洗、结构解析、语义切分、向量嵌入，并提供 5 阶段上下文组装流水线用于 LLM Q&A。

## MVP 1.0 核心能力

- TXT 上传 → 自动流水线：清洗 → 章节识别 → 段落切分 → 场景组装 → 向量化 → ChromaDB
- 基于向量检索 + LLM 的小说内容问答
- ONNX 本地推理嵌入（BGE-Small-ZH），零 API 费用
- bge-reranker-base 重排增强
- RabbitMQ 异步流水线，支持 1000+ 章小说
- 5 阶段 RAG 上下文组装：重排 → 去重 → 合并 → 预算分配 → 最终组装

## 快速开始

详见 `CLAUDE.md` 和 `DOCKER_COMPOSE_GUIDE.md`。

```bash
# One-click start (requires Docker Desktop)
.\scripts\start-all.ps1
```

## 模块架构

11 个 Maven 模块，DDD 分层：

| 模块 | 职责 |
|------|------|
| `domain/` | 核心实体与仓储接口 |
| `application/` | 应用服务编排及 MQ 消费者 |
| `interfaces/` | REST API 控制器 |
| `batch-processing/` | 流水线编排 (Load→Split→Embed) |
| `text-processing/` | NLP 章节识别/场景组装 |
| `embedding/` | ONNX 嵌入 + ChromaDB 存储 |
| `retrieval/` | RAG 检索与查询构建 |
| `context-assembler/` | 5 阶段上下文组装流水线 |
| `llm-client/` | DeepSeek/Gemini/Coze/Ollama 统一客户端 |
| `novelDownloader/` | 网络爬虫（辅助功能） |
| `novel-splitter-web/` | React 19 前端 |

## 文档

- `CLAUDE.md` — 完整开发指南
- `DOCKER_COMPOSE_GUIDE.md` — Docker 操作手册
- `docs/UserManual.md` — 用户手册
- `docs/todo.md` — 已知问题与路线图
- `docs/decisions.md` — 关键架构决策记录

## License

[Internal Tool]
