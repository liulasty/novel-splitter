# 删除异步化设计（慢删除 UX）

日期：2026-08-01
状态：已批准（设计评审通过）

## 问题背景

删除知识库/版本时，`DELETE /api/knowledge/id/{novelId}` 端点耗时 **41.6 秒**。根因：`KnowledgeBaseServiceImpl.deleteKnowledgeBaseById` 在请求内**同步执行 ChromaDB 向量删除**（含超时重试 + 两次 filter 删除）。前端弹窗在整个 41s 内 `isPending` 阻塞转圈。

日志还暴露第二个问题：`CleanupWorker: Cleanup task 20 not found in database, skipping` —— MQ 消息在 `@Transactional` 事务提交前发出，worker 立刻消费却查不到任务记录（经典事务竞态），异步清理实际没跑起来。

## 目标

- 删除端点**快速返回**（~200ms），前端 ~1s 内完成"乐观移除"。
- 重活（Chroma 向量删除、文件删除）由已存在的 `CleanupWorker` **异步执行**。
- 修掉 `Cleanup task not found` 事务竞态。
- 整书删除与逐版本删除一并异步化。

## 决策（已确认）

1. **范围**：后端异步 + 前端乐观。
2. **覆盖**：整书删除（`deleteKnowledgeBaseById` / `deleteKnowledgeBase`）+ 逐版本删除（`deleteVersion`）都异步。

## 范围界定

**做**：
- 后端三处删除方法删掉同步 `deleteVectorsForEntireNovel` / `deleteVectorsForVersionProfile`。
- MQ 发送改为事务提交后（`@TransactionalEventListener(AFTER_COMMIT)`）。
- 前端 toast 文案微调。
- 后端单测：删除端点不再同步调 `vectorStore.delete`、仍返回 cleanupTaskId、事件提交后触发 MQ。

**不做**：
- 改 `CleanupWorker` 主体逻辑（已具备按 NOVEL_ID / VERSION 删 Chroma + 文件的能力，仅受竞态影响）。
- 前端加清理任务状态轮询（小说已消失，用户无需感知向量清理完成的精确时刻）。
- 场景表删除移到 worker（它是毫秒级 DB 操作，留在端点事务内，保证端点返回后 DB 状态一致）。

## 后端改动

### 1. 移除同步 Chroma 删除

`KnowledgeBaseServiceImpl` 三个方法：
- `deleteKnowledgeBaseById`（~line 197）删掉 `deleteVectorsForEntireNovel(...)`。
- `deleteKnowledgeBase`（~line 157）删掉 `deleteVectorsForEntireNovel(...)`。
- `deleteVersion`（~line 118）删掉 `deleteVectorsForVersionProfile(...)`。

保留：`sceneRepository.deleteNovelById` / `deleteByProfile`（DB 快操作）、`CleanupTask` 落库、purge 任务行。

### 2. 事务提交后发 MQ（修竞态）

- 事务方法内发布 Spring `ApplicationEvent`（如 `CleanupTaskCreatedEvent(cleanupTaskMessage)`），不再直接 `rabbitTemplate.convertAndSend`。
- 新增 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` 处理器：
  ```java
  @EventListener
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCleanupTaskCreated(CleanupTaskCreatedEvent event) {
      rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE_NAME, "cleanup", event.getMessage());
  }
  ```
- 效果：任务记录先提交落库，worker 消费时能查到 → 竞态消除。

### 3. CleanupWorker

主体不改。竞态修复后它会真正执行（按 NOVEL_ID / VERSION 删 Chroma + 文件 + 标记 SUCCESS）。`deleteVersion` 的 VERSION 分支已按 (novelId, version, chunk) 构造 filter，无需改。

## 前端改动

- `KnowledgePage.tsx` 删除成功 toast 文案改为：
  「知识库 "X" 已删除，向量数据后台清理中（清理任务 N）」。
- 其余无结构改动（弹窗关闭、列表失效刷新现状即合理；后端变快后自然 ~1s）。

## 边界情况

| 场景 | 行为 |
|---|---|
| 后台清理失败（Chroma 超时） | worker 标记 FAILED + 日志；小说/场景已删，向量成孤儿（可接受，后续可补清扫） |
| 端点快速返回后立刻重传同书 | 软删幂等；向量由 worker 清理 |
| 运行中任务 | 端点先查 `hasActiveTasksForNovelId` → 409，前端禁用删除 |
| AFTER_COMMIT 发 MQ 失败 | 处理器内异常记录日志；任务留 PENDING，可人工补发 |
| 逐版本删除 | 同样异步，worker 按 (novelId, version, chunk) 删对应向量 |

## 测试

- **后端**：
  - 单测断言 `deleteKnowledgeBaseById` / `deleteVersion` **不再调用** `vectorStore.delete`（同步重活移除）。
  - 单测断言仍返回 cleanupTaskId、仍落库 `CleanupTask`。
  - 单测断言事务方法内发布 `CleanupTaskCreatedEvent`（而非直接 `convertAndSend`）。
- **前端**：`npm run build` + 手工 E2E：删整书 → ~1s 内消失 + toast 提示后台清理；删单版本 → 即时。
