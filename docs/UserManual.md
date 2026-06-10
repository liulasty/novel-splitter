# Novel Splitter 用户手册

## 概述

Novel Splitter 是一个中文网络小说处理工具，将 TXT 格式的原始小说文件，经过**章节解析 → 语义切分 → 向量化**流水线处理后，构建为 RAG-ready 的知识库，支持基于 LLM 的小说问答。

### 系统架构

```
用户上传 .txt → Load（章节解析）→ Split（场景切分）→ Embed（向量化）→ ChromaDB
                                                                      ↓
用户提问 ───────────────────────────────────────────→ RAG检索 + LLM回答
```

全部通过 RabbitMQ 异步消息驱动，前端轮询进度。

---

## 快速启动

### 前置条件

- Docker Desktop（Windows / macOS）
- 至少 4GB 可用内存（推荐 8GB+）
- 如使用本地 GPU 向量化：ONNX Runtime 自动加载，无需手动安装

### 一键启动（推荐）

```bash
.\scripts\start-all.ps1
```

启动后访问 `http://localhost` 进入前端界面。

### 5 个容器

| 服务 | 端口 | 说明 |
|---|---|---|
| PostgreSQL | 5432 | 业务数据（小说、章节、场景、任务） |
| RabbitMQ | 5672 / 15672 | 异步任务队列 + 管理界面 |
| ChromaDB | 8000 | 向量数据库 |
| Backend (Spring Boot) | 8080 | REST API |
| Frontend (Vite + React) | 80 | Web UI |

### 更多启动方式

```bash
# 启动全部（先编译后端）
.\scripts\start-all.ps1 -Build

# 仅启动基础设施（本地 IDE 开发时使用）
.\scripts\start-infra.ps1

# 调试模式：重建后端+前端容器并实时看日志
.\scripts\debug.ps1
```

---

## 前端页面指南

| 路由 | 页面 | 功能 |
|---|---|---|
| `/` | 聊天问答 | 选择小说 → 输入问题 → 获得基于 RAG 的回答 |
| `/knowledge` | 知识库管理 | 查看已入库小说的场景列表、版本管理、删除知识库 |
| `/ingest` | 入库处理 | 上传小说 → 选择处理流程（解析/切分/向量化） |
| `/tasks` | 任务监控 | 查看所有任务的进度、日志，分页筛选 |
| `/tasks/dlq` | 死信队列 | 查看积压的死信消息，一键重投回主队列 |
| `/tasks/load` | Load 任务 | 仅查看章节解析任务 |
| `/tasks/split` | Split 任务 | 仅查看场景切分任务 |
| `/tasks/embed` | Embed 任务 | 仅查看向量化任务 |
| `/tasks/pipeline` | Pipeline 任务 | 全流程流水线任务 |
| `/settings` | 系统设置 | 动态配置 LLM 参数、切分参数、RAG 参数等 |
| `/system` | 系统管理 | DLQ 监控、数据统计 |
| `/chroma-admin` | ChromaDB 管理 | 向量库健康检查、诊断、导出、重置 |
| `/debug` | RAG 调试 | 预览 RAG 检索到的上下文，不调用 LLM |

---

## 核心工作流

### 第 1 步：上传小说

在「入库处理」页面上传 `.txt` 文件（最大 50MB）。可选填标题、作者、描述。

或者通过 API：

```bash
curl -X POST http://localhost:8080/api/novels/upload \
  -F "file=@/path/to/novel.txt" \
  -F "title=小说名" \
  -F "author=作者"
```

### 第 2 步：章节解析（Load）

上传完成后，点击「章节解析」或将小说加入入库队列。系统使用正则识别章节标题（如"第一章"、"第1章"等），将原文拆分为章节结构并保存。

### 第 3 步：场景切分（Split）

章节解析完成后，点击「场景切分」。系统将每个章节的文本进一步按语义切分为更小的**场景（Scene）**单元，这是 RAG 检索的基本单位。

可配置参数：
- `chunkSize`：场景目标长度（默认 350 字）
- `chunkOverlap`：相邻场景重叠字数（默认 65 字）
- `version`：切分版本标识（不同参数组合使用不同 version 隔离）

### 第 4 步：向量化（Embed）

场景切分完成后，点击「向量化」。系统使用 ONNX Runtime 运行 BGE-Small-ZH 模型，将每个场景转为向量，存入 ChromaDB。

### 第 5 步：聊天问答

向量化完成后，进入聊天页面，选择已入库的小说，输入问题即可获得基于 RAG 的回答。

支持多个 LLM 后端：DeepSeek（默认）、Gemini、Coze、Ollama。

---

## 页面功能详解

### 聊天问答（`/`）

- **选书**：从下拉框选择已入库（向量化完成）的小说
- **追问**：在同一小说下连续提问
- **参数调节**：可调节 TopK（检索数量）、Max Scenes（最大场景数）、Max Context Tokens等
- **知识溯源**：回答附带引用来源的场景 ID，可在知识库页面查看原文

### 入库处理（`/ingest`）

页面分为三个区：

1. **上传区**：文件上传 + 元信息填写
2. **配置区**：切分参数（chunkSize、chunkOverlap、version）
3. **操作区**：Load / Split / Embed / Pipeline 按钮。选中小说后可以选择流程

**URL 参数**：支持 `?novelId=xxx` 直接定位到已上传的小说，在上次会话的小说上继续操作。

### 知识库管理（`/knowledge`）

- 查看选定小说的所有场景（分页展示）
- 查看该小说的所有切分版本（version + chunkSize + chunkOverlap）
- 删除指定版本的知识库（同时清理 PostgreSQL 场景记录 + ChromaDB 向量）
- 删除整部小说的全部知识库

### 任务监控（`/tasks`）

按 novelId、taskType（LOAD/SPLIT/EMBED/PIPELINE）、状态（PENDING/PROCESSING/SUCCESS/FAILED）、时间范围查询。

每个任务可查看：
- 任务状态和进度
- 历史事件时间线
- 错误信息（失败时）

### 系统设置（`/settings`）

分 category 组织的动态配置：

| 分类 | 配置项 |
|---|---|
| LLM Provider | provider 选择、各 LLM 的 base-url / api-key / model |
| RAG | topK、max-scenes、max-context-tokens、max-answer-tokens |
| Split | chunk-size、chunk-overlap、target-length |
| Embedding | sub-batch-size、rate-limit |
| System | api-auth-token、vite-api-timeout、存储路径等 |

所有配置优先读取 DB 覆盖值，无覆盖则回退到 `application.yml` 默认值。

### ChromaDB 管理（`/chroma-admin`）

- **健康检查**：Chroma 服务器是否可达
- **版本诊断**：对比 PostgreSQL 场景记录与 Chroma 向量的同步状态，发现孤儿数据
- **统计数据**：当前集合的向量总数
- **导出**：流式导出 Chroma 数据为 JSON
- **重建集合**：删除并重建 Chroma 集合，同时清理本地数据库数据
- **重置**：清空所有向量

### RAG 调试（`/debug`）

输入问题和小说信息，预览 RAG 检索结果，包括：
- 检索到的场景列表及相关性分数
- 5 阶段上下文组装结果（重排序 → 去重 → 合并 → Token 预算 → 最终组装）
- **不调用 LLM**，仅查看检索上下文

### 死信队列（`/tasks/dlq`）

- 查看各队列的死信积压数量
- 一键重投死信消息回主队列（可指定最大重投条数）

---

## API 参考

### 小说管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/novels` | 列出本地存储的 .txt 文件 |
| GET | `/api/novels/summaries?scope=all\|embed_ready` | 获取小说摘要列表 |
| GET | `/api/novels/stats` | 每本小说的入库统计 |
| DELETE | `/api/novels/{novelId}` | 软删除小说 |
| POST | `/api/novels/upload` | 上传小说文件 (multipart/form-data) |

### 流水线处理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/novels/{novelId}/load` | 独立章节解析 |
| POST | `/api/novels/{novelId}/split` | 章节解析（别名，全流程时使用） |
| POST | `/api/novels/{novelId}/scene-split` | 场景切分 |
| POST | `/api/novels/{novelId}/split/retry` | 重试场景切分（跳过章节解析） |
| POST | `/api/novels/{novelId}/embed` | 向量化 |
| POST | `/api/novels/{novelId}/pipeline` | 多阶段流水线 |
| POST | `/api/novels/{novelId}/re-parse-chapters` | 强制重解析章节 |

### 章节与场景查询

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/novels/{novelId}/chapters` | 章节树 |
| GET | `/api/novels/{novelId}/chapters/{chapterId}/scenes` | 章节下的场景（分页） |

### 聊天与 RAG

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat` | 聊天问答 |
| POST | `/api/v1/rag` | RAG 问答 |
| POST | `/api/v1/rag/debug` | RAG 调试预览（不调 LLM） |

### 知识库管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/knowledge/scenes/lightweight` | 分页查看所有场景 |
| GET | `/api/knowledge/id/{novelId}/scenes` | 指定小说的场景列表 |
| GET | `/api/knowledge/id/{novelId}/versions` | 指定小说的版本列表 |
| GET | `/api/knowledge/id/{novelId}/split-profiles` | 结构化切分数据集 |
| DELETE | `/api/knowledge/id/{novelId}` | 删除整部小说的知识库 |
| DELETE | `/api/knowledge/id/{novelId}/versions/{version}` | 删除指定切分数据集 |

### 任务管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/tasks` | 全部任务列表 |
| GET | `/api/tasks/list` | 分页查询（可选筛选条件） |
| GET | `/api/tasks/{taskId}` | 单个任务详情 |
| GET | `/api/tasks/{taskId}/events` | 任务事件日志 |
| DELETE | `/api/tasks/{taskId}` | 删除任务（运行中的不可删） |

### 系统管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/settings` | 获取全量配置 |
| POST | `/api/settings` | 保存单条配置 |
| DELETE | `/api/settings/{id}` | 按 ID 删除配置 |
| DELETE | `/api/settings/key?key=xxx` | 按 key 删除配置 |
| GET | `/api/system/dlq/stats` | DLQ 积压统计 |
| POST | `/api/system/dlq/{queueName}/requeue` | 重投死信消息 |
| GET | `/api/admin/chroma/stats` | Chroma 统计 |
| GET | `/api/admin/chroma/healthcheck` | 健康检查 |
| GET | `/api/admin/chroma/diagnostics` | 版本诊断 |
| POST | `/api/admin/chroma/reset` | 重置 Chroma |
| POST | `/api/admin/chroma/collections/rebuild` | 重建集合 |
| GET | `/api/admin/vector/stats` | 向量库统计 |
| POST | `/api/admin/vector/search` | 向量相似度搜索 |
| GET | `/api/stats/dashboard` | 大盘统计 |
| GET | `/api/system/health/models` | 模型健康状态 |

### 下载与预览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/download` | 从 URL 下载小说 |
| POST | `/api/v1/download/ingest` | 下载并入库 |
| POST | `/api/split/preview` | 预览切分效果（内存中执行，不入库） |

---

## 配置说明

主要配置文件：`config/.env.dev`（开发环境）、`config/.env.prod`（生产环境）。

### 关键环境变量

| 变量 | 说明 | 默认值 |
|---|---|---|
| `DB_HOST` | PostgreSQL 地址 | `localhost`（Docker 中为 `postgres`） |
| `RABBITMQ_HOST` | 消息队列地址 | `localhost` |
| `CHROMA_URL` | 向量数据库地址 | `http://localhost:8000` |
| `NOVEL_LLM_PROVIDER` | 默认 LLM 提供商 | `deepseek` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | （必填） |
| `API_AUTH_TOKEN` | API 统一鉴权 Token | （建议设置） |
| `BACKEND_PORT` | 后端端口 | `8080` |
| `FRONTEND_PORT` | 前端端口 | `80` |
| `STORAGE_ROOT_PATH` | 文件存储根目录 | `data/novel-storage` |

### LLM 配置

在「系统设置」页面或 `application.yml` 中切换：

- **DeepSeek**：默认，需 `DEEPSEEK_API_KEY`
- **Gemini**：需 `GEMINI_API_KEY`
- **Coze**：需 `COZE_API_KEY` + `COZE_BOT_ID`
- **Ollama**：本地部署，无需 API Key，地址 `http://localhost:11434`

---

## 脚本参考

全部脚本位于 `scripts/` 目录，提供 `.ps1`（Windows）、`.sh`（Linux/Mac）、`.bat`（备用）三种格式。

| 脚本 | 功能 |
|---|---|
| `start-all.ps1` | 启动全部 5 个服务，可选 `-Build` 先编译后端 |
| `start-infra.ps1` | 仅启动 PostgreSQL + RabbitMQ + ChromaDB |
| `stop.ps1` | 停止服务（后接 `dev` / `prod`） |
| `debug.ps1` | 重建并重启后端+前端，持续输出日志 |
| `deploy.ps1` | 完整部署（后接 `dev` / `prod`） |
| `build.ps1` | Maven 编译 + Docker 镜像构建 |
| `svc.ps1` | 管理单个服务：`svc.ps1 restart backend`、`svc.ps1 logs frontend`、`svc.ps1 ps` |
| `version.ps1` | 查看当前 Maven 版本 |
| `reset-infra-data.ps1` | 清空所有持久化数据 |
| `reconcile.sql` | 数据对账 SQL（PostgreSQL 孤儿数据检查） |

---

## 数据流与任务状态

### 状态机

```
PENDING → LOADING → LOAD_DONE → SPLITTING → SPLIT_DONE → EMBEDDING → EMBED_DONE
  │         │           │           │           │            │
  └──── ← ─┴── ← ──────┴── ← ──────┴── ← ──────┴── ← ──────┘  (任一阶段可失败)
                      ↓
                   FAILED
```

### 幂等性

- 同一 `novelId + version` 重试场景切分不会重复生成场景
- 同一场景重试向量化不会重复写入向量
- 正在向量化的小说拒绝新的场景切分请求（返回 HTTP 409）

### 场景版本化

每个场景记录关联 `(novelId, version)`。不同切分参数（chunkSize、chunkOverlap）应使用不同的 version 值隔离，避免数据混淆。

---

## 故障排查

### 后端启动失败

检查 Docker 日志：

```bash
.\scripts\svc.ps1 logs backend
```

常见原因：

| 现象 | 原因 | 解决 |
|---|---|---|
| `Connection refused` to postgres | DB_HOST 指向错误 | Docker 中必须为 `postgres` |
| `NoClassDefFoundError` | Jar 未构建 | 运行 `mvn clean package -DskipTests` |
| `Chroma connection failed` | ChromaDB 未就绪 | 等 Chroma 启动后再重启 backend |
| `API_AUTH_TOKEN mismatch` | Token 不一致 | 检查前端 `.env.dev` 和后端配置 |

### 前端白屏 / API 报错

```bash
.\scripts\svc.ps1 logs frontend
```

常见原因：
- `VITE_API_PROXY_TARGET` 指向的后端端口不对
- `API_AUTH_TOKEN` 前后端不匹配（前端 localStorage 中的 Token）

### 向量化失败

查看 Embed 任务的错误事件：

```
GET /api/tasks/{taskId}/events
```

常见原因：
- ONNX 模型加载失败（检查 `EMBEDDING_ONNX_MODEL_PATH`）
- ChromaDB 不可达
- OOM（尝试调小 `splitter.embed.sub-batch-size`）

### 死信消息处理

当消息处理失败达到重试上限后会进入死信队列：

1. 访问 `/tasks/dlq` 查看各队列积压
2. 点击"重投"将消息重新发送到主队列
3. 如持续失败，检查任务事件的错误日志

### Git Bash 下 curl 中文报错

Windows Git Bash 终端会自动将中文转为 GBK 编码，导致请求失败：

```bash
# 在 PowerShell 或 CMD 中执行 curl，或使用：
.\scripts\svc.ps1 logs backend
```

---

## 常见操作场景

### 场景 A：快速体验

```bash
.\scripts\start-all.ps1          # 启动全部服务
# 浏览器打开 http://localhost
# 入库处理 → 上传小说 → Pipeline → 等待完成
# 聊天 → 选择小说 → 提问
```

### 场景 B：调整切分参数重新切分

在入库处理页面修改 chunkSize / chunkOverlap → 场景切分 → 向量化。

或使用不同 version：

```bash
curl -X POST "http://localhost:8080/api/novels/{novelId}/scene-split" \
  -H "Content-Type: application/json" \
  -d '{"version": "v2", "chunkSize": 500, "chunkOverlap": 100}'
```

### 场景 C：清理并重新处理

```bash
# 删除知识库（场景 + 向量）
curl -X DELETE "http://localhost:8080/api/knowledge/id/{novelId}"

# 重新加载 + 切分 + 向量化
curl -X POST "http://localhost:8080/api/novels/{novelId}/pipeline" \
  -H "Content-Type: application/json" \
  -d '{"stages": ["LOAD", "SPLIT", "EMBED"]}'
```

### 场景 D：切换 LLM 提供商

在系统设置页面修改 `llm.provider` 为 `gemini`/`coze`/`ollama`，并填入对应的 API Key。

或通过 API：

```bash
curl -X POST "http://localhost:8080/api/settings" \
  -H "Content-Type: application/json" \
  -d '{"configKey": "llm.provider", "configValue": "gemini", "category": "llm"}'
```

---

## 业务错误码

当 API 返回非 200 状态码时，响应体中的 `code` 字段为业务错误码：

| 范围 | 类别 |
|---|---|
| 1000–1999 | 通用错误（参数校验、资源不存在） |
| 2000–2999 | 小说管理（上传、下载） |
| 3000–3999 | 任务管理（状态冲突、阶段不支持） |
| 4000–4999 | RAG / 问答 |
| 5000–5999 | 向量数据库 |
| 6000–6999 | 死信队列 |
| 7000–7999 | 认证授权 |

---

## 已知限制

- 聊天响应为同步阻塞，暂不支持 SSE 流式输出
- 未实现移动端适配（有 PRD 设计但未编码）
- 无内置性能 profiling（1000+ 章小说建议监控内存）
- ChromaDB 与 PostgreSQL 之间无分布式事务（重试机制降低不一致概率）
