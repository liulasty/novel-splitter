# /process 页多 Tab 重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/process` 页三个管线阶段（章节解析 / 场景切分 / 向量化入库）拆成三个 tab，并把 16KB+ 的 `ProcessingPanel` 拆为骨架 + 三个聚焦子组件。

**Architecture:** 共享类型抽到 `ProcessTypes.ts`；新建 `ParseTab` / `SplitTab` / `EmbedTab` 三个子组件（都消费现有 `useProcessTask` 的 `state`/`actions` + 派生门控 `gates`）；`ProcessingPanel` 改为骨架（小说选择器 + tab 栏 + 活动 tab + 全局任务状态），`activeTab` 存 URL `?tab=`。

**Tech Stack:** React 19 + Vite + TypeScript + react-router-dom v7 (`useSearchParams`)。无测试框架，每个任务用 `npm run build`（tsc）验证。

**规范文档:** `docs/superpowers/specs/2026-08-01-process-multitab-design.md`

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `novel-splitter-web/src/pages/Process/components/ProcessTypes.ts` | **新建**共享类型：`ProcessState` / `ProcessActions` / `ProcessGates` |
| `novel-splitter-web/src/pages/Process/components/ParseTab.tsx` | **新建**章节解析 tab（策略/正则/解析/校对 + ChapterReviewModal） |
| `novel-splitter-web/src/pages/Process/components/SplitTab.tsx` | **新建**场景切分 tab（版本复合控件/chunk 参数/切分/预览 + SplitPreviewModal） |
| `novel-splitter-web/src/pages/Process/components/EmbedTab.tsx` | **新建**向量化入库 tab（只读摘要 + 仅向量化） |
| `novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx` | **重构**为骨架（小说选择器 + tab 栏 + 活动 tab + 任务状态），删除内联区块 |

---

### Task 1: 抽共享类型 ProcessTypes.ts

**Files:**
- Create: `novel-splitter-web/src/pages/Process/components/ProcessTypes.ts`
- Modify: `novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx`（改用导入的类型）

- [ ] **Step 1: 创建 ProcessTypes.ts**

```ts
import type { SplitTask } from "@/api/taskApi";
import type { SceneSplitProfileDto } from '@/api/knowledgeApi';

export interface ProcessState {
  currentNovelId: string;
  version: string;
  profiles: SceneSplitProfileDto[];
  currentProfile?: SceneSplitProfileDto;
  maxTokens: number;
  overlapTokens: number;
  chapterReviewAck: boolean;
  chapterTitleRegex: string;
  recognitionStrategy: string;
  tasks: SplitTask[];
  activeTasks: SplitTask[];
  poller: {
    errorCount: number;
    isPaused: boolean;
    stuckTaskIds: string[];
    timeoutTaskIds: string[];
  };
  isChapterParsing: boolean;
  isSceneSplitting: boolean;
  isEmbedding: boolean;
}

export interface ProcessActions {
  setVersion: (v: string) => void;
  setMaxTokens: (v: number) => void;
  setOverlapTokens: (v: number) => void;
  setChapterTitleRegex: (v: string) => void;
  setRecognitionStrategy: (v: string) => void;
  acknowledgeChapterReview: () => void;
  handleChapterParse: () => void;
  handleSceneSplit: (triggerEmbed: boolean) => void;
  handleForceReparseChapters: () => void;
  handleEmbed: () => void;
  manualRefresh: () => Promise<void>;
  selectNovelById: (novelId: string) => void;
  clearSelectedNovel: () => void;
  addActiveTask: (taskId: string) => void;
}

/** ProcessingPanel 派生的门控布尔，各 tab 用于按钮禁用 */
export interface ProcessGates {
  chapterParseBusy: boolean;
  chapterParseSucceeded: boolean;
  structurallyReady: boolean;
  canSceneSplit: boolean;
}
```

- [ ] **Step 2: ProcessingPanel 改用导入类型**

`ProcessingPanel.tsx` 顶部加 `import type { ProcessState, ProcessActions } from './ProcessTypes';`，把 `ProcessingPanelProps` 的 `state`/`actions` 内联类型替换为 `state: ProcessState; actions: ProcessActions;`。

- [ ] **Step 3: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Process/components/ProcessTypes.ts \
        novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx
git commit -m "refactor(web): 抽取 Process 页共享类型 ProcessState/ProcessActions"
```

### Task 2: ParseTab.tsx（章节解析）

**Files:**
- Create: `novel-splitter-web/src/pages/Process/components/ParseTab.tsx`

- [ ] **Step 1: 创建组件**

内容从 ProcessingPanel 当前解析相关 JSX 提取（识别策略、CUSTOM 正则、解析/重解析/校对按钮、ChapterReviewModal），`chapterReviewOpen` 状态在此持有。`version` 来自 state（ChapterReviewModal 需要）：

```tsx
import { useState } from 'react';
import { Loader2, FileText, RefreshCw, ClipboardCheck } from "lucide-react";
import { ChapterReviewModal } from '@/pages/Ingest/components/ChapterReviewModal';
import type { ProcessState, ProcessActions, ProcessGates } from './ProcessTypes';

interface ParseTabProps {
  state: ProcessState;
  actions: ProcessActions;
  gates: ProcessGates;
  currentNovelStatus?: string;
}

export function ParseTab({ state, actions, gates, currentNovelStatus }: ParseTabProps) {
  const { currentNovelId, recognitionStrategy, chapterTitleRegex, version, isChapterParsing } = state;
  const [chapterReviewOpen, setChapterReviewOpen] = useState(false);

  return (
    <div className="space-y-5">
      {/* 识别策略 + 正则 */}
      <div className="grid grid-cols-1 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">识别策略</label>
          <select
            value={recognitionStrategy}
            onChange={(e) => actions.setRecognitionStrategy(e.target.value)}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            <option value="PLAIN">普通章节</option>
            <option value="VOLUME_CHAPTER">分卷章节</option>
            <option value="CUSTOM">自定义正则</option>
          </select>
        </div>
        {recognitionStrategy === 'CUSTOM' && (
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">章节标题正则（可选）</label>
            <input
              type="text"
              value={chapterTitleRegex}
              onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
              placeholder="整行匹配 Java 正则，留空用默认"
              className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
            />
          </div>
        )}
      </div>

      {/* 状态提示 */}
      {currentNovelId && (
        <p className="rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-2 text-[11px] text-slate-600 leading-relaxed">
          当前书状态：<span className="font-mono text-slate-700">{currentNovelStatus ?? '（列表外会话）'}</span>
          {gates.chapterParseBusy ? ' · 章节任务进行中…' : null}
          {!gates.structurallyReady && !gates.chapterParseBusy ? ' · 完成章节解析后可校对并场景切分' : null}
        </p>
      )}

      {/* 操作按钮 */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
        <button
          type="button"
          onClick={actions.handleChapterParse}
          disabled={!currentNovelId || isChapterParsing || gates.chapterParseBusy}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          {isChapterParsing || gates.chapterParseBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
          ① 解析章节
        </button>
        <button
          type="button"
          onClick={actions.handleForceReparseChapters}
          disabled={!currentNovelId || gates.chapterParseBusy}
          title="独立 Load API，force=true，可选用上方章节正则"
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full text-xs font-medium border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          强制重解析
        </button>
        <button
          type="button"
          onClick={() => setChapterReviewOpen(true)}
          disabled={!currentNovelId}
          className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full text-xs font-medium border border-indigo-200 bg-indigo-50 text-indigo-800 hover:bg-indigo-100 transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          <ClipboardCheck className="w-3.5 h-3.5" />
          章节校对
        </button>
      </div>

      {/* 章节校对 Modal（状态在此持有） */}
      {currentNovelId && (
        <ChapterReviewModal
          open={chapterReviewOpen}
          novelId={currentNovelId}
          version={version}
          onClose={() => setChapterReviewOpen(false)}
          onAcknowledge={actions.acknowledgeChapterReview}
          onReparseTaskCreated={(taskId) => actions.addActiveTask(taskId)}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过（组件暂未被引用，但编译通过）。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Process/components/ParseTab.tsx
git commit -m "feat(web): 新增 ParseTab 章节解析 tab 组件"
```

### Task 3: SplitTab.tsx（场景切分）

**Files:**
- Create: `novel-splitter-web/src/pages/Process/components/SplitTab.tsx`

- [ ] **Step 1: 创建组件**

内容从 ProcessingPanel 当前切分相关 JSX 提取（版本复合控件、chunk 参数、切分/切分并入库/预览按钮、SplitPreviewModal），`previewOpen` 状态在此持有：

```tsx
import { useState } from 'react';
import { Loader2, FileText, Eye } from "lucide-react";
import { SplitPreviewModal } from '@/pages/Ingest/components/SplitPreviewModal';
import { splitProfileLabel } from '@/api/knowledgeApi';
import type { ProcessState, ProcessActions, ProcessGates } from './ProcessTypes';

interface SplitTabProps {
  state: ProcessState;
  actions: ProcessActions;
  gates: ProcessGates;
  currentNovelStatus?: string;
}

export function SplitTab({ state, actions, gates, currentNovelStatus }: SplitTabProps) {
  const {
    currentNovelId, version, profiles, currentProfile, maxTokens, overlapTokens,
    chapterReviewAck, isSceneSplitting,
  } = state;
  const [previewOpen, setPreviewOpen] = useState(false);

  return (
    <div className="space-y-5">
      {/* 版本 + chunk 参数 */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本标识</label>
          <select
            value={profiles.some((p) => p.version === version) ? version : ''}
            onChange={(e) => {
              const v = e.target.value;
              if (!v) return;
              actions.setVersion(v);
              const p = profiles.find((x) => x.version === v);
              if (p && p.chunkSize != null) actions.setMaxTokens(p.chunkSize);
              if (p && p.chunkOverlap != null) actions.setOverlapTokens(p.chunkOverlap);
            }}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            <option value="">{profiles.length ? '选择已有版本…' : '暂无已生成版本'}</option>
            {profiles.map((p) => (
              <option key={p.version} value={p.version}>{splitProfileLabel(p)}</option>
            ))}
          </select>
          <input
            type="text"
            value={version}
            onChange={(e) => actions.setVersion(e.target.value)}
            placeholder="或输入新版本名，如 v2"
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
          {currentProfile && (
            <p className="text-[11px] text-slate-400">
              已选数据集：块大小 {currentProfile.chunkSize} · 重叠 {currentProfile.chunkOverlap}
            </p>
          )}
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">场景块大小（字）</label>
          <input
            type="number"
            value={maxTokens}
            onChange={(e) => actions.setMaxTokens(Number(e.target.value))}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">重叠（字）</label>
          <input
            type="number"
            value={overlapTokens}
            onChange={(e) => actions.setOverlapTokens(Number(e.target.value))}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
      </div>

      {/* 状态提示 */}
      {currentNovelId && (
        <p className="rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-2 text-[11px] text-slate-600 leading-relaxed">
          当前书状态：<span className="font-mono text-slate-700">{currentNovelStatus ?? '（列表外会话）'}</span>
          {gates.chapterParseBusy ? ' · 章节任务进行中…' : null}
          {gates.structurallyReady && !chapterReviewAck && !gates.chapterParseBusy ? (
            <span> · 请打开「章节校对」并确认无误后再场景切分</span>
          ) : null}
          {!gates.structurallyReady && !gates.chapterParseBusy ? ' · 完成章节解析后可校对并场景切分' : null}
        </p>
      )}

      {/* 操作按钮 */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
        <button
          type="button"
          onClick={() => actions.handleSceneSplit(false)}
          disabled={!currentNovelId || !gates.canSceneSplit || isSceneSplitting}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-indigo-600 hover:from-violet-600 hover:to-indigo-700 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          {isSceneSplitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
          ② 场景切分
        </button>
        <button
          type="button"
          onClick={() => actions.handleSceneSplit(true)}
          disabled={!currentNovelId || !gates.canSceneSplit || isSceneSplitting}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-fuchsia-500 to-purple-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          ② 切分并入库
        </button>
        <button
          type="button"
          onClick={() => setPreviewOpen(true)}
          disabled={!currentNovelId}
          className="inline-flex items-center gap-2 h-9 px-4 py-2 rounded-full text-sm font-medium text-indigo-700 bg-indigo-50 border border-indigo-100 hover:bg-indigo-100 transition-colors disabled:opacity-40 disabled:pointer-events-none"
        >
          <Eye className="w-4 h-4" /> 预览效果
        </button>
      </div>

      {/* 切分预览 Modal（状态在此持有） */}
      {currentNovelId && (
        <SplitPreviewModal isOpen={previewOpen} onClose={() => setPreviewOpen(false)} novelId={currentNovelId} version={version} />
      )}
    </div>
  );
}
```

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Process/components/SplitTab.tsx
git commit -m "feat(web): 新增 SplitTab 场景切分 tab 组件"
```

### Task 4: EmbedTab.tsx（向量化入库）

**Files:**
- Create: `novel-splitter-web/src/pages/Process/components/EmbedTab.tsx`

- [ ] **Step 1: 创建组件**

```tsx
import { CheckCircle, Loader2 } from "lucide-react";
import type { ProcessState, ProcessActions } from './ProcessTypes';

interface EmbedTabProps {
  state: ProcessState;
  actions: ProcessActions;
}

export function EmbedTab({ state, actions }: EmbedTabProps) {
  const { currentNovelId, version, currentProfile, isEmbedding } = state;

  return (
    <div className="space-y-5">
      {/* 只读摘要 */}
      <div className="rounded-xl border border-slate-200 bg-slate-50/50 px-4 py-3">
        {currentProfile ? (
          <p className="text-sm text-slate-600">
            将向量化 <span className="font-mono font-semibold text-slate-800">{version}</span>
            （块大小 {currentProfile.chunkSize} · 重叠 {currentProfile.chunkOverlap}）
          </p>
        ) : (
          <p className="text-sm text-amber-700">
            版本 <span className="font-mono font-semibold">{version || '（未选择）'}</span> 尚未切分完成，
            请先到「场景切分」生成数据集。
          </p>
        )}
      </div>

      {/* 操作按钮 */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
        <button
          type="button"
          onClick={actions.handleEmbed}
          disabled={!currentNovelId || isEmbedding}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-600 hover:to-teal-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          {isEmbedding ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
          ③ 仅向量化
        </button>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Process/components/EmbedTab.tsx
git commit -m "feat(web): 新增 EmbedTab 向量化入库 tab 组件"
```

### Task 5: ProcessingPanel 骨架化 + tab 栏 + URL activeTab

**Files:**
- Modify: `novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx`（整体重构）

- [ ] **Step 1: 重构为骨架**

替换整个文件为（保留小说选择器与任务状态区，新增 tab 栏，删除内联配置/指令/按钮/Modal，改渲染三个子组件）：

```tsx
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { ListChecks, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { TaskPollerStatus } from '@/pages/Ingest/components/TaskPollerStatus';
import type { ProcessState, ProcessActions, ProcessGates } from './ProcessTypes';
import { ParseTab } from './ParseTab';
import { SplitTab } from './SplitTab';
import { EmbedTab } from './EmbedTab';

interface ProcessingPanelProps {
  state: ProcessState;
  actions: ProcessActions;
}

const TABS = [
  { id: 'parse', label: '章节解析' },
  { id: 'split', label: '场景切分' },
  { id: 'embed', label: '向量化入库' },
] as const;
type TabId = (typeof TABS)[number]['id'];

export function ProcessingPanel({ state, actions }: ProcessingPanelProps) {
  const { currentNovelId, tasks, activeTasks, poller } = state;
  const [searchParams, setSearchParams] = useSearchParams();

  const { data: novelOptions = [] } = useQuery({
    queryKey: ['novelSummaries', 'all'],
    queryFn: () => novelApi.getNovelSummaries('all'),
  });

  const currentMeta = novelOptions.find((n) => n.novelId === currentNovelId);
  const chapterParseBusy = tasks.some(
    (t) =>
      t.novelId === currentNovelId &&
      (t.taskType === 'CHAPTER_PARSE' || t.taskType === 'LOAD') &&
      (t.status === 'PENDING' || t.status === 'PROCESSING')
  );
  const chapterParseSucceeded = tasks.some(
    (t) =>
      t.novelId === currentNovelId &&
      (t.taskType === 'CHAPTER_PARSE' || t.taskType === 'LOAD') &&
      t.status === 'SUCCESS'
  );
  const structurallyReady =
    ['PARSED', 'SPLIT_COMPLETED', 'COMPLETED'].includes(currentMeta?.status ?? '') || chapterParseSucceeded;
  const canSceneSplit =
    !!currentNovelId && structurallyReady && state.chapterReviewAck && !chapterParseBusy;

  const gates: ProcessGates = { chapterParseBusy, chapterParseSucceeded, structurallyReady, canSceneSplit };

  const tabParam = searchParams.get('tab');
  const activeTab: TabId = TABS.some((t) => t.id === tabParam) ? (tabParam as TabId) : 'parse';
  const setActiveTab = (tab: TabId) => {
    setSearchParams((prev) => {
      const p = new URLSearchParams(prev);
      p.set('tab', tab);
      return p;
    }, { replace: true });
  };

  return (
    <div className="rounded-2xl border-2 border-dashed border-indigo-200 bg-gradient-to-br from-indigo-50/60 via-white to-violet-50/40 p-6 relative">

      {/* Current novel selector（共享，不变） */}
      <div className="mb-5 rounded-xl border border-slate-200 bg-white/80 px-4 py-3 space-y-3">
        <div className="flex items-center justify-between gap-2 flex-wrap">
          <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-1.5">
            <ListChecks className="w-3.5 h-3.5" />
            当前操作的小说
          </span>
          {currentNovelId ? (
            <button
              type="button"
              onClick={() => actions.clearSelectedNovel()}
              className="text-xs text-slate-500 hover:text-red-600 inline-flex items-center gap-1 shrink-0"
            >
              <XCircle className="w-3.5 h-3.5" />
              清除选择
            </button>
          ) : null}
        </div>

        <div className="space-y-2">
          <p className="text-[11px] text-slate-500">
            从书库选择已上传的小说。先<strong>解析章节</strong>（Load），再<strong>场景切分</strong>（Split）。
            同一本书可用不同场景版本号生成多套切片。
          </p>
          <div className="max-h-48 overflow-y-auto rounded-lg border border-slate-200 bg-white divide-y divide-slate-100">
            {currentNovelId && !novelOptions.some((n) => n.novelId === currentNovelId) ? (
              <button
                type="button"
                onClick={() => actions.selectNovelById(currentNovelId)}
                className={cn(
                  'w-full text-left px-3 py-2.5 text-sm transition-colors',
                  'bg-amber-50/80 text-amber-900 hover:bg-amber-50'
                )}
              >
                <span className="font-medium">当前会话（未在书库列表中）</span>
                <span className="block text-xs font-mono text-amber-800/80 mt-0.5 break-all">{currentNovelId}</span>
              </button>
            ) : null}
            {novelOptions.length === 0 && !(currentNovelId && !novelOptions.some((n) => n.novelId === currentNovelId)) ? (
              <div className="px-3 py-6 text-center text-sm text-slate-400">
                暂无已登记小说，请前往「上传入库」页面上传文件。
              </div>
            ) : null}
            {novelOptions.map((n) => {
              const selected = n.novelId === currentNovelId;
              return (
                <button
                  key={n.novelId}
                  type="button"
                  onClick={() => actions.selectNovelById(n.novelId)}
                  className={cn(
                    'w-full text-left px-3 py-2.5 text-sm transition-colors',
                    selected
                      ? 'bg-blue-50 text-blue-900 ring-inset ring-1 ring-blue-100'
                      : 'text-slate-800 hover:bg-slate-50'
                  )}
                >
                  <span className="font-medium line-clamp-1">{n.title || n.novelId}</span>
                  <span className="block text-xs text-slate-500 mt-0.5">
                    {n.status ?? '?'} · <span className="font-mono">{n.novelId}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {currentNovelId ? (
          <p className="text-[11px] text-slate-500 font-mono break-all border-t border-slate-100 pt-2">
            当前 novelId: {currentNovelId}
          </p>
        ) : null}
      </div>

      {/* Tab 栏 */}
      <div className="mb-5 flex gap-1 rounded-xl border border-slate-200 bg-white/80 p-1">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setActiveTab(t.id)}
            className={cn(
              'flex-1 h-9 rounded-lg text-sm font-medium transition-colors',
              activeTab === t.id ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* 活动 tab 内容 */}
      <div className="mb-5">
        {activeTab === 'parse' && <ParseTab state={state} actions={actions} gates={gates} currentNovelStatus={currentMeta?.status} />}
        {activeTab === 'split' && <SplitTab state={state} actions={actions} gates={gates} currentNovelStatus={currentMeta?.status} />}
        {activeTab === 'embed' && <EmbedTab state={state} actions={actions} />}
      </div>

      {/* 全局任务状态（所有 tab 可见） */}
      <TaskPollerStatus tasks={activeTasks} poller={poller} onManualRefresh={actions.manualRefresh} />
    </div>
  );
}
```

注意：重构后 `previewOpen`/`chapterReviewOpen` 两个 `useState` 必须删除（Modal 状态已下放子组件）。若保留会导致 `noUnusedLocals` 报错。`useState` import 若不再使用也应移除。

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。修复任何未用变量/import 报错。

- [ ] **Step 3: 人工核对**

确认 ProcessingPanel 不再有内联的识别策略/版本/chunk/按钮/Modal JSX；`useProcessTask` 未改动；`ProcessPage.tsx` 未改动。

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Process/components/ProcessingPanel.tsx
git commit -m "feat(web): /process 页改为三阶段 tab（骨架 + Parse/Split/EmbedTab）"
```

### Task 6: 验证收口

**Files:**
- 无（验证任务）

- [ ] **Step 1: 前端构建**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 成功。

- [ ] **Step 2: 前端容器重建并重启**

后端未改，仅前端改了：`docker compose --env-file config/.env.dev up -d --build frontend`
（或 `.\scripts\svc.ps1 restart frontend` + 手动 `docker compose build frontend`）。

- [ ] **Step 3: 手工 E2E（覆盖 spec 测试清单）**

浏览器验证 http://localhost:3000/process：

| # | 场景 | 预期 |
|---|---|---|
| 1 | 三 tab 渲染 | 默认「章节解析」，仅显示策略/正则/解析/校对；切「场景切分」显示版本/chunk/切分/预览；切「向量化入库」显示只读摘要+仅向量化 |
| 2 | 操作门控 | 未解析小说：切分 tab 按钮灰掉；解析完成+校对确认后可用 |
| 3 | 切 tab 不丢状态 | 切分 tab 选 v2 → 切到入库 tab → 回切分 tab，v2 仍在 |
| 4 | Modal 归属 | 「章节校对」只在解析 tab 打开 ChapterReviewModal；「预览效果」只在切分 tab 打开 SplitPreviewModal |
| 5 | `?tab=` 深链 | `/process?novelId=X&tab=split` 直达切分 tab；`?tab=xxx` 回退解析 tab |
| 6 | 全局任务状态 | TaskPollerStatus 在三个 tab 下都可见 |

- [ ] **Step 4: 无代码提交（E2E 通过即收口）**

---

## Self-Review 结果

- **Spec 覆盖**：三 tab 划分（Task 5 tab 栏）、小说选择器共享（Task 5 保留）、版本/chunk 参数在切分 tab + 入库只读摘要（Task 3/4）、自由切换+按钮门控（Task 5 `gates` + 各 tab 按钮 disabled）、任务状态全局（Task 5 保留）、`?tab=` URL（Task 5）、Modal 下放（Task 2/3）。全数覆盖，无缺口。
- **占位符**：无 TBD/TODO；每个组件完整代码。
- **类型一致性**：`ProcessState`/`ProcessActions`/`ProcessGates` 在 Task 1 定义，Task 2-5 一致引用；`ParseTab`/`SplitTab` 均接收 `gates` + `currentNovelStatus`，`EmbedTab` 只收 `state`/`actions`，与 Task 5 的调用一致。
