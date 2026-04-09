# 基于 MQ 的异步切分与入库架构设计

> **实现状态 (Implementation Status):**
> 规划的 **3 队列架构（Load、Split、Embed）** 现已使用 **RabbitMQ** 和 **`ProgressSseService`** 完全实现。

## 1. 需求分析与功能边界确认

### 核心需求
- **异步化切分**：将原本同步的“小说上传 -> 切分 -> 入库”流程拆分为“小说上传 -> 发送任务 -> 异步处理”。
- **削峰填谷**：通过 RabbitMQ 缓冲大量并发上传的文件切分任务，防止系统 OOM 或超时。
- **任务状态追踪**：提供可视化的进度和状态管理（等待中、切分中、成功、失败、日志记录）。
- **前端完善**：在前端各个页面（如 IngestPage, SystemPage 等）增加对当前架构、数据流向和使用方式的具体文字说明。

### 功能边界
- **上传服务（Producer）**：接收文件，保存原始文件，向 MQ 发送包含文件路径、任务 ID 的消息，记录任务初始状态。
- **消息队列（RabbitMQ）**：存储和路由切分任务，支持重试、死信队列（DLQ）。
- **切分服务（Worker / Consumer）**：监听 MQ，拉取任务，执行文档读取、Markdown 分段、语义切分、向量化，最后存入 ChromaDB，并更新任务状态。
- **可视化后台**：提供任务进度查询接口，前端展示任务列表和进度条。提供 RabbitMQ 管理台入口。

## 2. 领域模型与核心对象关系设计

- **SplitTask (切分任务)**：
  - `taskId`: 唯一任务ID (UUID)
  - `novelId`: 小说ID (去除 .txt)
  - `fileName`: 原始文件名
  - `status`: 任务状态 (PENDING, PROCESSING, SUCCESS, FAILED)
  - `progress`: 进度百分比 (0-100)
  - `message`: 日志或错误信息
  - `createdAt`, `updatedAt`: 时间戳

## 3. 数据模型与持久化设计

- **任务状态存储**：可以使用本地 H2/SQLite 数据库，或者简单的基于文件的 JSON 存储（如 `tasks_store.json`）来保存 `SplitTask` 状态，以便前端轮询查询。
- **向量存储**：ChromaDB（独立 Docker 部署）。
- **文件存储**：现有的本地文件系统（`raw/` 存放原文件，`splits/` 存放切分结果）。

## 4. 接口抽象与系统边界设计

### 4.1. 上传接口更新
- `POST /api/novel/upload`
  - 行为：保存文件到本地 -> 生成 `SplitTask` -> 发送 MQ 消息 -> 返回 `taskId`。

### 4.2. 任务状态接口 (新增)
- `GET /api/tasks`：获取所有切分任务列表。
- `GET /api/tasks/{taskId}`：获取单个任务状态。
- `DELETE /api/tasks/{taskId}`：清除任务记录。

### 4.3. MQ 消息结构定义
```json
{
  "taskId": "uuid",
  "novelId": "novel_name",
  "filePath": "/absolute/path/to/novel.txt"
}
```

## 5. 系统架构与部署视角设计

- **Web 前端 (React)**：通过轮询或 WebSocket 获取任务进度。页面增加说明文案。
- **Spring Boot 后端**：
  - **Producer 模块**：集成 `spring-boot-starter-amqp`，配置 RabbitTemplate。
  - **Consumer 模块**：配置 `@RabbitListener`，调用现有的 `SplitService` 和 `VectorStore`。
  - **Task Store 模块**：内存或本地数据库维护任务状态。
- **中间件**：RabbitMQ (Docker: `rabbitmq:3-management`，暴露 5672 和 15672 端口)。ChromaDB (Docker)。

## 6. 前端页面说明文字设计 (Web 补充)

- `IngestPage.tsx`：增加说明：“本页面用于上传小说文档。上传后任务将发送至 RabbitMQ 异步队列，由后台 Worker 消费并进行切分入库。可在下方查看实时进度。”
- `SystemPage.tsx`：增加说明：“系统状态监控。此处可跳转至 RabbitMQ 控制台查看队列堆积情况，以及监控 ChromaDB 向量库状态。”
- `KnowledgePage.tsx`：增加说明：“此处展示已成功完成切分并存入 ChromaDB 的知识库文件。仍在队列中的文件请在上传页查看。”
- `ChatPage.tsx` / `RagDebugPage.tsx`：增加关于 RAG 检索流程的简短说明。

## 7. 实施路径 (Implementation Roadmap)
1. **基础环境搭建**：提供 RabbitMQ docker-compose 配置，引入 AMQP 依赖。
2. **任务管理与状态存储**：实现 Task 实体和 Repository，提供增删改查 REST API。
3. **MQ 生产者与消费者**：重构 `NovelController` 和 `SplitService`，实现基于 RabbitMQ 的解耦。
4. **前端改造**：对接 Task API，实现进度条和日志展示，补充各页面的说明文字。
