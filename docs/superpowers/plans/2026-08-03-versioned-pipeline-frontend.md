# 版本化流水线改造 · 前端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 `/ingest` 与 `/process` 页面：`/ingest` = 阶段一（上传 + 章节格式枚举选择 + 原子基准解析）；`/process` = 阶段二/三（版本实验：创建版本 → 切分 → 向量化 → 激活）。

**Architecture:** 沿用现有 React 19 + Vite + Zustand?（实际用 TanStack Query + useState hooks）+ Tailwind 组件风格。在 `novelApi.ts` 扩展版本端点 API；`/ingest` 新增 `BaselineParsePanel`；`/process` 把三 tab 重构为版本实验视图，版本数据源从「scenes 派生 profiles」切换为「`GET /novels/{id}/versions` 返回 NovelVersionDto」。

**Tech Stack:** React 19 · TypeScript · Vite · TanStack Query · TailwindCSS 4 · lucide-react · react-router（`?novelId=`、`?tab=` 深链）

**前置:** 后端已交付（`26e57c0` 后端重构 + `b4c2ad1` 修复），新端点已就绪：
- `GET /api/novels/{id}/versions` → `NovelVersionDto[]`
- `POST /api/novels/{id}/versions`（CreateVersionRequest）
- `POST /api/novels/{id}/baseline`（ReparseChaptersRequestDto）
- `POST /api/novels/{id}/versions/{v}/split`、`/embed`、`/activate`
- `DELETE /api/novels/{id}/versions/{v}`

**验证:** 前端无测试框架，沿用 `npm run build`（tsc 类型校验）+ 浏览器手工 E2E。

---

### Task F1: API 层扩展（版本端点 + 类型）

**Files:**
- Modify: `novel-splitter-web/src/api/novelApi.ts`

- [ ] **Step 1: 加类型**

在 `novelApi.ts` 加：

```ts
export interface NovelVersionDto {
  novelId: string;
  versionTag: string;
  splitStrategy?: string | null;
  chunkSize?: number | null;
  chunkOverlap?: number | null;
  status: string;
  splitCursorChapterIndex?: number | null;
  splitCursorSceneSeq?: number | null;
  embedRunId?: string | null;
  embedCursorSceneSeq?: number | null;
  collectionName?: string | null;
  activatedAt?: number | null;
  createdAt?: number | null;
  updatedAt?: number | null;
  active: boolean;
}

export interface CreateVersionRequest {
  versionTag?: string;
  splitStrategy?: string;
  chunkSize?: number;
  chunkOverlap?: number;
}
```

- [ ] **Step 2: 加端点方法**（`novelApi` 对象内）

```ts
listVersions: async (novelId: string): Promise<NovelVersionDto[]> => {
  const r = await apiClient.get<ApiEnvelope<NovelVersionDto[]>, NovelVersionDto[]>(
    `/novels/${encodeURIComponent(novelId)}/versions`);
  return r;
},
createVersion: async (novelId: string, body: CreateVersionRequest): Promise<NovelVersionDto> => {
  const r = await apiClient.post<ApiEnvelope<NovelVersionDto>, NovelVersionDto>(
    `/novels/${encodeURIComponent(novelId)}/versions`, body);
  return r;
},
baselineParse: async (novelId: string, body?: ReparseChaptersRequest): Promise<TaskSubmitResponse> => {
  const r = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
    `/novels/${encodeURIComponent(novelId)}/baseline`, body ?? {});
  return r;
},
startVersionSplit: async (novelId: string, versionTag: string): Promise<TaskSubmitResponse> => {
  const r = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
    `/novels/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(versionTag)}/split`);
  return r;
},
startVersionEmbed: async (novelId: string, versionTag: string): Promise<TaskSubmitResponse> => {
  const r = await apiClient.post<ApiEnvelope<TaskSubmitResponse>, TaskSubmitResponse>(
    `/novels/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(versionTag)}/embed`);
  return r;
},
activateVersion: async (novelId: string, versionTag: string): Promise<void> => {
  await apiClient.post<ApiEnvelope<void>, void>(
    `/novels/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(versionTag)}/activate`);
},
deleteVersion: async (novelId: string, versionTag: string): Promise<void> => {
  await apiClient.delete<ApiEnvelope<void>, void>(
    `/novels/${encodeURIComponent(novelId)}/versions/${encodeURIComponent(versionTag)}`);
},
```

- [ ] **Step 3: 验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过，无类型错误。

---

### Task F2: /ingest 新增 BaselineParsePanel（阶段一）

**Files:**
- Create: `novel-splitter-web/src/pages/Ingest/components/BaselineParsePanel.tsx`
- Modify: `novel-splitter-web/src/pages/IngestPage.tsx`
- Modify: `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts`（如需）

- [ ] **Step 1: 设计组件**

`BaselineParsePanel` 职责：选择章节格式枚举 → 预览解析 → 确认提交阶段一原子任务 → 轮询结果。

Props：`{ novelId?: string; onDone?: () => void }`（novelId 为空时禁用并提示先上传）。用 `useQuery` 拉 `listChapterStrategies()`（后端返回 `ChapterStrategy[]`：key/label/description）。

结构：
- 策略单选（`CN_CHAPTER`/`CN_BACK`/`CN_SECTION`/`EN_CHAPTER`/`PROLOGUE`/`VOLUME_CHAPTER`/`CUSTOM`），label 用后端返回。
- CUSTOM 选中时显示正则输入框。
- 「预览解析」按钮：调 `getChapters` 不行（需先解析）——改为**提交即解析**的流程：先调 `baselineParse`，轮询任务成功后展示 `getChapters()` 结果（章节数 + 前 N 章标题）并 toast 成功；失败 toast「已回滚，无残留」。
- 提供「前往 /process 做版本实验」链接（novelId 深链）。

- [ ] **Step 2: 状态与数据流**

用 `useMutation`(baselineParse) + 成功后 `useQuery(['chapters', novelId], novelApi.getChapters)` 展示章节列表；轮询用现有 `useTaskPoller` 或简单 `useQuery` 轮询任务状态。复用现有 `getApiErrorMessage` / `handleConflict409`。

- [ ] **Step 3: 集成进 IngestPage**

`IngestPage` 在 `UploadPanel` 下方渲染 `BaselineParsePanel`（`novelId` 来自 `useIngestTask` 的当前选中 novel 或上传返回的 novelId）。上传成功后自动选中该书并让阶段一面板可用。

- [ ] **Step 4: 验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过。

---

### Task F3: /process 重构为版本实验视图（阶段二/三）

**Files:**
- Modify: `novel-splitter-web/src/pages/ProcessPage.tsx`
- Modify: `novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx`
- Create: `novel-splitter-web/src/pages/Process/components/VersionExperimentPanel.tsx`
- Create: `novel-splitter-web/src/pages/Process/components/VersionRow.tsx`
- Create: `novel-splitter-web/src/pages/Process/components/VersionCreateForm.tsx`
- Modify: `novel-splitter-web/src/pages/Process/hooks/useProcessTask.ts`
- （可选保留/复用）`ParseTab.tsx`、`SplitTab.tsx`、`EmbedTab.tsx`

- [ ] **Step 1: 版本数据源切换**

`useProcessTask` 中用 `useQuery(['versions', novelId], () => novelApi.listVersions(novelId), { enabled: !!novelId })` 替换/补充 `useSplitVersion` 的 profiles 派生。保留 `useSplitVersion` 给 Chat/RagDebug 用（不改那些页面），但 /process 主数据源用版本列表。

- [ ] **Step 2: 新增 mutations**

`useProcessTask` 新增：`createVersionMutation`、`startSplitMutation`、`startEmbedMutation`、`activateVersionMutation`、`deleteVersionMutation`。成功后 `invalidateQueries(['versions', novelId])` + toast。复用 `addActiveTask`（split/embed 返回 taskId）。

- [ ] **Step 3: ProcessingPanel 重构**

- 保留共享小说选择器（只列基准就绪/已登记小说，维持现状）。
- 移除三 tab 结构，改渲染 `VersionExperimentPanel`。
- 保留 `TaskPollerStatus`（全局任务状态）。

`VersionExperimentPanel`：
- `VersionCreateForm`：输入 versionTag（空则自动 v2/v3…）、splitStrategy 下拉（SCENE_BOUNDARY/OVERLAP_CHUNK/SEMANTIC）、chunkSize、chunkOverlap；「创建版本」按钮 → createVersion → 选中新版本。
- 版本列表 `VersionRow[]`，每个 row 按 status 渲染操作 gate：
  - `PENDING` → 「发起切分」
  - `SPLITTING`/`EMBEDDING` → 显示游标进度（已切 X 章 / 已向量化 X 场景）+「续传」（调同一切分/向量化接口）
  - `SPLIT_DONE` → 「发起向量化」
  - `EMBED_DONE` → 「激活」（原子切换，显示当前 ACTIVE 徽标）
  - `ACTIVE` → 高亮「检索中」徽标，可「切换回旧版本」（激活其它 EMBED_DONE 版本）
  - `FAILED`/`ABANDONED` → 状态徽标 +「删除版本」（`deleteVersion`）
  - 每行显示：versionTag、splitStrategy/chunk/overlap、status 徽标（配色沿用现有 phase 风格）、activatedAt/createdAt、collectionName（可读）。

- **门控**：基准未就绪（novel 未 PARSED）时版本创建禁用并提示「请先在 /ingest 完成章节解析」。

- [ ] **Step 4: 章节策略默认值对齐**

`useProcessTask` 的 `recognitionStrategy` 默认 `'PLAIN'` 改为 `'CN_CHAPTER'`（后端已把 PLAIN 兼容映射为 CN_CHAPTER，但新默认用新枚举值）。

- [ ] **Step 5: 验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc 通过。

---

### Task F4: 全量验证 + 统一提交

**Files:**
- （无新文件；验证性任务）

- [ ] **Step 1: 全量构建**

Run: `cd novel-splitter-web && npm run build`
Expected: BUILD 成功，无类型错误。

- [ ] **Step 2: 浏览器手工 E2E（用户侧）**

1. `/ingest`：上传小说 → 选 CN_CHAPTER → 解析 → 显示 5 章 → 前往 /process。
2. `/process`：创建 v1 → 切分（进度）→ 向量化 → 激活（ACTIVE 徽标）→ 重复切分提示「已跳过（幂等）」。
3. 版本 FAILED/ABANDONED 展示与删除。
4. 深链 `?novelId=` 存活。

- [ ] **Step 3: 提交**

```bash
git add novel-splitter-web/src && git commit -m "feat(web): /ingest 阶段一(章节枚举+原子解析)、/process 版本实验视图(创建/切分/向量化/激活)"
```

---

## Self-Review

- **Spec 覆盖**：/ingest=阶段一（F2）✓；/process=版本实验+激活（F3）✓；API 层（F1）✓；策略枚举对齐（F3 Step 4）✓；`useSplitVersion` 保留给 Chat/RagDebug（不越界）✓。
- **已知依赖**：F3 依赖 F1 的 API；F2 依赖 `listChapterStrategies` 已存在。
- **不做**：不改 /chat、/rag-debug、/knowledge 的结构（仅 /process 数据源切换不影响它们）。
