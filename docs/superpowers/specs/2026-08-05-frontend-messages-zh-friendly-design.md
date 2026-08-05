# 前端用户反馈文案中文友好化 设计

## Context

前端提示整体已是中文，但有两类"非友好"残留：
1. **后端英文报错透传**：`lib/apiError.ts` 的 `getApiErrorMessage` 直接把后端英文 `message`（如 `Novel not found: …`、`Task is running; cannot delete…`）原样抛给用户。
2. **少量调试页 toast 英文混排**：`TaskSplitPage` / `TaskLoadPage` / `TaskPipelinePage` 的 `Split 任务已提交（含 Load）` 等。

目标：让用户看到的反馈类文案为友好中文。**领域术语保留**（novelId / version / maxScenes / Token / ChromaDB / REBUILD 等）。空态/状态经排查已基本为中文，无需改动；RagDebug 调试页不在本次范围。

## 方案

### 1. 后端英文报错中文化（核心）

修改 `novel-splitter-web/src/lib/apiError.ts`：在 `getApiErrorMessage` 解析出原始 message 后，经 `zhFriendlyError(raw)` 映射器处理再返回。

映射规则（正则 + 关键词，按序匹配；命中已知模式 → 友好中文；已是中文或未命中 → 原样返回）：

| 后端英文 | 友好中文 |
|---|---|
| `Novel not found: X` | 该小说不存在或已被删除 |
| `Novel has running tasks; cannot delete right now.` | 该小说存在运行中任务，暂不可删除，请等待完成后再试 |
| `Novel has running tasks; cannot delete knowledge base right now.` | 该小说存在运行中任务，暂不可删除知识库 |
| `Task is running; cannot delete. Please wait…` | 任务运行中，暂不可删除，请等待完成后再试 |
| `Task not found: X` | 任务不存在或已被清理 |
| `Version not found: X` | 版本不存在 |
| `Collection … not found` | 向量集合不存在 |
| 兜底 `not found` | 数据不存在或已被删除 |

所有现有调用 `getApiErrorMessage` 的地方自动受益，无需改调用点。

### 2. 少量调试页 toast 中文化

- `Split 任务已提交（含 Load）` → `切分任务已提交（含章节解析）`
- `Load 任务已提交` → `章节解析任务已提交`
- `Pipeline 已提交` → `流水线任务已提交`

### 3. 明确不做

- RagDebug 页英文界面（不在本次范围）
- 领域术语、字段名、占位符（novelId / version / maxScenes / Token / REBUILD 等）
- 后端英文文案本身（仅前端消化）

## 涉及文件

- `novel-splitter-web/src/lib/apiError.ts`（新增 `zhFriendlyError` + 接入 `getApiErrorMessage`）
- `novel-splitter-web/src/pages/tasks/TaskSplitPage.tsx`（toast 文案）
- `novel-splitter-web/src/pages/tasks/TaskLoadPage.tsx`（toast 文案）
- `novel-splitter-web/src/pages/tasks/TaskPipelinePage.tsx`（toast 文案）

## 验证

- `npx tsc -b` + `eslint` 通过
- 浏览器触发一次 `Novel not found` 类后端报错，确认 toast 显示中文而非英文原文
- 回归：正常成功/失败的 toast 文案不受影响
