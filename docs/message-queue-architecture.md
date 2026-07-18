# Novel Splitter 消息队列架构说明文档

## 1. 概述
在 Novel Splitter (小说切分与 RAG 预处理系统) 中，消息队列（RabbitMQ）承担了整个异步流水线的“大动脉”角色。它不仅解耦了耗时的文本处理逻辑，还确保了在大文件处理过程中的系统稳定性。

本文档详细描述了消息队列在系统各个阶段的责任、消息流向序列图、消息契约标准（JSON Schema）以及死信队列（DLQ）的架构规划。

---

## 2. 消息流向序列图 (Sequence Diagram)

系统采用典型的 **事件驱动架构 (EDA)**，通过消息触发核心 Worker 的串行协作。以下序列图展示了核心任务在不同阶段的消息流向：

```mermaid
sequenceDiagram
    participant WebAPI as NovelFacadeServiceImpl (Web API)
    participant TaskSvc as TaskService / Publisher
    participant Exchange as DirectExchange (novel.task.exchange)
    participant Fanout as FanoutExchange (novel.task.notify.exchange)
    participant LoadQ as Queue (novel.task.load)
    participant SplitQ as Queue (novel.task.split)
    participant EmbedQ as Queue (novel.task.embed)
    participant CleanupQ as Queue (novel.task.cleanup)
    participant LoadW as LoadWorker
    participant SplitW as SplitWorker
    participant EmbedW as EmbedWorker
    participant CleanupW as CleanupWorker
    participant Frontend as Frontend (轮询)

    %% 1. Ingestion Pipeline
    rect rgb(240, 248, 255)
    Note over WebAPI, EmbedW: 阶段 1: 任务调度与流转 (Ingestion Pipeline)
    WebAPI->>Exchange: 1. 发送 SplitTaskMessage
    Note right of WebAPI: RK: "load"
    Exchange->>LoadQ: 路由到 load 队列
    LoadQ->>LoadW: 消费消息
    LoadW-->>Exchange: 2. 文本加载完成，发送 SplitTaskMessage
    Note right of LoadW: RK: "split"
    Exchange->>SplitQ: 路由到 split 队列
    SplitQ->>SplitW: 消费消息
    SplitW-->>Exchange: 3. 智能切分完成，发送 EmbedTaskMessage
    Note right of SplitW: RK: "embed"
    Exchange->>EmbedQ: 路由到 embed 队列
    EmbedQ->>EmbedW: 消费消息 (入库向量DB)
    end

    %% 2. 状态查询 (Polling Feedback)
    rect rgb(255, 248, 240)
    Note over WebAPI, TaskSvc: 阶段 2: 状态查询 (Polling Feedback)
    LoadW->>TaskSvc: 更新进度/状态至 DB
    SplitW->>TaskSvc: 更新进度/状态至 DB
    EmbedW->>TaskSvc: 更新进度/状态至 DB
    Frontend->>WebAPI: 轮询查询任务进度 (每 2~3 秒)
    WebAPI->>TaskSvc: 读取 DB 最新状态
    TaskSvc-->>Frontend: 返回任务进度
    end

    %% 3. Maintenance Phase
    rect rgb(240, 255, 240)
    Note over WebAPI, CleanupW: 阶段 3: 资源清理 (Maintenance Phase)
    WebAPI->>Exchange: 1. 触发删除，发送 CleanupTaskMessage
    Note right of WebAPI: RK: "cleanup"
    Exchange->>CleanupQ: 路由到 cleanup 队列
    CleanupQ->>CleanupW: 消费消息并执行级联删除
    end
```

---

## 3. 系统流程串联说明

### 3.1 阶段 1：任务调度与流转 (Ingestion Pipeline)
解耦耗时操作，实现背压控制。
*   **加载任务 (Load Phase)**：`LoadWorker` 消费 `novel.task.load` 队列，负责将 TXT 文件加载到内存并初步清洗。完成后发送消息到 `split` 路由键。
*   **智能切分 (Split Phase)**：`SplitWorker` 消费 `novel.task.split` 队列，执行章节识别和 Scene 组装。完成后发送消息到 `embed` 路由键。
*   **向量化入库 (Embed Phase)**：`EmbedWorker` 消费 `novel.task.embed` 队列，调用 Embedding 模型并将向量批量写入 ChromaDB。

### 3.2 阶段 2：状态查询 (Polling Feedback)
提供可观测性，目前采用轮询机制。
*   各个 Worker (Load, Split, Embed) 在处理过程中，通过 `TaskService` 将进度和状态实时更新并持久化到数据库。
*   前端通过定时（如每 2~3 秒）轮询 Web API 接口读取最新任务状态。考虑到长耗时任务特性，此频率的轮询体验与 SSE 差异不大，且大大降低了系统复杂度。
*   轮询接口统一为 `GET /api/tasks/poll`，支持 `ids`/`taskIds` 双参数兼容（最多 20 个）与 `novelId` 兜底恢复。
*   轮询调度为智能策略：`PROCESSING=2s`，仅 `PENDING=3s`，页面隐藏降频 `10s`，隐藏超过 `5min` 暂停，连续失败指数退避并可手动刷新。

### 3.3 阶段 3：资源清理 (Maintenance Phase)
保证最终一致性。
*   用户删除小说或版本时，系统发送消息到 `cleanup` 路由键。
*   `CleanupWorker` 消费 `novel.task.cleanup` 队列，异步执行物理文件和向量索引的级联删除。

---

## 4. 消息契约文档 (Message Contract Spec)

以下是核心消息体的数据契约标准（JSON Schema 格式），作为团队开发的接口标准。

### 4.1 SplitTaskMessage
**用途**: 触发文本加载和智能切分（复用于 Load 和 Split 阶段）。
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "SplitTaskMessage",
  "description": "用于触发文本加载和智能切分的消息契约",
  "type": "object",
  "required": ["taskId", "novelId", "version"],
  "properties": {
    "taskId": { "type": "string", "description": "系统全局唯一的任务ID" },
    "novelId": { "type": "string", "description": "关联的小说业务ID" },
    "version": { "type": "string", "description": "数据版本号，用于隔离不同切分策略的数据" },
    "maxScenes": { "type": "integer", "description": "切分上限，可选，用于限制最大处理量", "minimum": 1 }
  }
}
```

### 4.2 EmbedTaskMessage
**用途**: 触发向量化及向量数据库入库。
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "EmbedTaskMessage",
  "description": "用于触发向量化及向量数据库入库的消息契约",
  "type": "object",
  "required": ["taskId", "novelId", "version"],
  "properties": {
    "taskId": { "type": "string", "description": "系统全局唯一的任务ID" },
    "novelId": { "type": "string", "description": "关联的小说业务ID" },
    "version": { "type": "string", "description": "数据版本号" }
  }
}
```

### 4.3 TaskProgressEvent
**用途**: 任务处理链内部进度事件（当前前端不再依赖 SSE，统一使用 Polling）。
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "TaskProgressEvent",
  "description": "用于任务处理链内部进度记录与状态同步",
  "type": "object",
  "required": ["taskId", "status", "timestamp"],
  "properties": {
    "taskId": { "type": "string", "description": "任务ID" },
    "progress": { "type": "integer", "description": "处理进度百分比 (0-100)", "minimum": 0, "maximum": 100 },
    "message": { "type": "string", "description": "进度相关的易读文本，如 '正在向量化 40/100'" },
    "status": { 
      "type": "string", 
      "enum": ["PENDING", "PROCESSING", "SUCCESS", "FAILED"],
      "description": "任务当前状态枚举" 
    },
    "timestamp": { "type": "integer", "description": "事件发生的毫秒级时间戳" }
  }
}
```

### 4.4 PollingResponse (当前前端查询契约)
**用途**: 前端轮询任务状态最小字段集响应。
```json
{
  "taskId": "task-123",
  "status": "PROCESSING",
  "progress": 42,
  "message": "正在向量化 42/100",
  "updatedAt": 1712573000123,
  "serverTime": 1712573002456
}
```

### 4.5 CleanupTaskMessage
**用途**: 触发异步垃圾数据清理。
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CleanupTaskMessage",
  "description": "用于触发异步垃圾数据清理的消息契约",
  "type": "object",
  "required": ["cleanupTaskId", "targetId", "targetType"],
  "properties": {
    "cleanupTaskId": { "type": "integer", "description": "清理任务自身的数据库自增ID" },
    "targetId": { "type": "string", "description": "需要被清理的目标标识(通常为小说ID)" },
    "targetType": { 
      "type": "string", 
      "enum": ["NOVEL", "VERSION"], 
      "description": "清理范围：NOVEL=清理整个小说，VERSION=仅清理某一个版本" 
    },
    "version": { "type": "string", "description": "当 targetType 为 VERSION 时必填" }
  }
}
```

---

## 5. 死信队列 (DLQ) 架构（已实现 2026-05-30）

所有核心工作队列（load/split/embed/cleanup/enrich）均已配置 `x-dead-letter-exchange`，
失败消息在重试耗尽后自动路由至 DLQ。

### 架构

- **DLQ Exchange**: `novel.task.dlq.exchange` (DirectExchange)
- **DLQ Queue**: `novel.task.dlq` (持久化)
- **Binding Key**: `dlq.routing.key`

### 待完善

- `DlqWorker` 监控和手动消息重新投递管理后台尚未实现
- DLQ 告警通知（邮件/企业微信）未接入