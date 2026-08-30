# 前端用户反馈文案中文友好化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把前端用户可见的反馈文案（后端英文报错 + 少量调试页 toast）中文化，领域术语保留。

**Architecture:** 在 `lib/apiError.ts` 的 `getApiErrorMessage` 内部加一层 `zhFriendlyError` 映射（已知英文报错 → 友好中文，未知/中文原样），所有调用点自动受益；另改 3 处调试页 toast 文案。

**Tech Stack:** React 19, TypeScript, Vite。前端无测试框架，验证用 `tsc -b` + `eslint` + 浏览器手动验证。

**前置说明：** 前端项目无单元测试框架（仅 `tsc -b && vite build` + `eslint`），因此验证步骤为类型检查/构建 + 浏览器手动验证，不写单元测试。

---

### Task 1: 后端英文报错中文化（`lib/apiError.ts`）

**Files:**
- Modify: `novel-splitter-web/src/lib/apiError.ts`

- [ ] **Step 1: 在 `getApiErrorMessage` 前新增 `zhFriendlyError` 映射函数**

在 `apiError.ts` 的 `getApiErrorMessage` 函数上方加入：

```ts
/**
 * 已知后端英文报错 → 友好中文；已是中文或未命中模式则原样返回（保留调试信息）。
 * 顺序敏感：更具体的规则在前，兜底 `not found` 在最后。
 */
const ZH_ERROR_RULES: Array<{ pattern: RegExp; zh: string }> = [
  { pattern: /Novel has running tasks; cannot delete knowledge base right now\.?/i, zh: '该小说存在运行中任务，暂不可删除知识库，请等待完成后再试' },
  { pattern: /Novel has running tasks; cannot delete right now\.?/i, zh: '该小说存在运行中任务，暂不可删除，请等待完成后再试' },
  { pattern: /Novel not found/i, zh: '该小说不存在或已被删除' },
  { pattern: /Task is running; cannot delete/i, zh: '任务运行中，暂不可删除，请等待完成后再试' },
  { pattern: /Task not found/i, zh: '任务不存在或已被清理' },
  { pattern: /Version not found/i, zh: '版本不存在' },
  { pattern: /collection .* not found/i, zh: '向量集合不存在' },
  { pattern: /not found/i, zh: '数据不存在或已被删除' },
];

export function zhFriendlyError(raw: string): string {
  if (!raw) return raw;
  const msg = raw.trim();
  if (!msg) return msg;
  // 已是中文则原样返回
  if (/[一-龥]/.test(msg)) return msg;
  for (const { pattern, zh } of ZH_ERROR_RULES) {
    if (pattern.test(msg)) return zh;
  }
  return msg;
}
```

- [ ] **Step 2: 让 `getApiErrorMessage` 返回值经 `zhFriendlyError` 处理**

把 `getApiErrorMessage` 的三个返回分支改为 `zhFriendlyError(...)`：

```ts
export function getApiErrorMessage(error: unknown, fallback: string): string {
  const e = error as MaybeAxiosLikeError | undefined;
  const dataMessage = e?.response?.data?.message;
  if (typeof dataMessage === 'string' && dataMessage.trim().length > 0) {
    return zhFriendlyError(dataMessage);
  }
  const dataError = e?.response?.data?.error;
  if (typeof dataError === 'string' && dataError.trim().length > 0) {
    return zhFriendlyError(dataError);
  }
  if (typeof e?.message === 'string' && e.message.trim().length > 0) {
    return zhFriendlyError(e.message);
  }
  return fallback;
}
```

- [ ] **Step 3: 类型检查与 lint**

Run:
```
cd novel-splitter-web && npx tsc -b && npx eslint src/lib/apiError.ts
```
Expected: 无错误输出，exit 0。

- [ ] **Step 4: 提交**

```bash
git add novel-splitter-web/src/lib/apiError.ts
git commit -m "feat(web): 后端英文报错经 zhFriendlyError 映射为友好中文"
```

### Task 2: 调试页 toast 文案中文化

**Files:**
- Modify: `novel-splitter-web/src/pages/tasks/TaskSplitPage.tsx`（第 20 行 toast.success）
- Modify: `novel-splitter-web/src/pages/tasks/TaskLoadPage.tsx`（第 20 行 toast.success）
- Modify: `novel-splitter-web/src/pages/tasks/TaskPipelinePage.tsx`（第 29 行 toast.success）

- [ ] **Step 1: TaskSplitPage toast**

`TaskSplitPage.tsx` 第 20 行：
```ts
toast.success('Split 任务已提交（含 Load）');
```
改为：
```ts
toast.success('切分任务已提交（含章节解析）');
```

- [ ] **Step 2: TaskLoadPage toast**

`TaskLoadPage.tsx` 第 20 行：
```ts
toast.success('Load 任务已提交');
```
改为：
```ts
toast.success('章节解析任务已提交');
```

- [ ] **Step 3: TaskPipelinePage toast**

`TaskPipelinePage.tsx` 第 29 行：
```ts
toast.success('Pipeline 已提交');
```
改为：
```ts
toast.success('流水线任务已提交');
```

- [ ] **Step 4: 类型检查与 lint**

Run:
```
cd novel-splitter-web && npx tsc -b && npx eslint src/pages/tasks/TaskSplitPage.tsx src/pages/tasks/TaskLoadPage.tsx src/pages/tasks/TaskPipelinePage.tsx
```
Expected: 无错误输出，exit 0。

- [ ] **Step 5: 提交**

```bash
git add novel-splitter-web/src/pages/tasks/TaskSplitPage.tsx novel-splitter-web/src/pages/tasks/TaskLoadPage.tsx novel-splitter-web/src/pages/tasks/TaskPipelinePage.tsx
git commit -m "fix(web): 调试页 toast 文案中文化"
```

### Task 3: 浏览器验证

- [ ] **Step 1: 验证错误映射**

前端 dev 已由 Docker 运行。触发一次后端英文报错（如访问 `/process?novelId=<不存在的ID>` 或删除不存在的小说），确认 toast 显示中文（如"该小说不存在或已被删除"）而非英文原文。

- [ ] **Step 2: 验证 toast 文案**

进入 `/tasks/load`、`/tasks/split`、`/tasks/pipeline` 各提交一次，确认 toast 为中文文案。
