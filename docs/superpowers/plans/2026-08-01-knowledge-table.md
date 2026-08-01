# /knowledge 页表格化重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/knowledge` 页从状态分区卡片网格改为可搜索/过滤/排序的表格 + 可展开版本行，整书删除改用共享确认弹窗。

**Architecture:** 新建 `DeleteNovelModal` / `NovelTableRow` / `NovelTable` 三个组件；`KnowledgePage` 重组为「工具栏（搜索/过滤/排序）+ 表格 + 删除弹窗」；废弃 `NovelVersionsCard`。`phaseBadge` 从卡内提升到 `novelKnowledgePhase.ts` 共享。统计用现有 `getNovelStats` 合并，缺失显 `—`。

**Tech Stack:** React 19 + Vite + TypeScript + TanStack Query v5 + react-router-dom v7 + date-fns。无测试框架，每个任务用 `npm run build`（tsc）验证。

**规范文档:** `docs/superpowers/specs/2026-08-01-knowledge-table-design.md`

---

## 文件结构

| 文件 | 职责 |
|---|---|
| `novel-splitter-web/src/pages/Knowledge/novelKnowledgePhase.ts` | 修改：新增导出 `phaseBadge`（原在 NovelVersionsCard 内部） |
| `novel-splitter-web/src/pages/Knowledge/components/DeleteNovelModal.tsx` | **新建**整书删除确认弹窗（含 purge 勾选） |
| `novel-splitter-web/src/pages/Knowledge/components/NovelTableRow.tsx` | **新建**表格行 + 可展开版本明细 |
| `novel-splitter-web/src/pages/Knowledge/components/NovelTable.tsx` | **新建**表格（列头 + 行 + 空态） |
| `novel-splitter-web/src/pages/KnowledgePage.tsx` | 重组：工具栏 + 表格 + 删除弹窗 |
| `novel-splitter-web/src/pages/Knowledge/components/NovelVersionsCard.tsx` | **删除**（被表格取代） |

---

### Task 1: DeleteNovelModal + 共享 phaseBadge

**Files:**
- Create: `novel-splitter-web/src/pages/Knowledge/components/DeleteNovelModal.tsx`
- Modify: `novel-splitter-web/src/pages/Knowledge/novelKnowledgePhase.ts`

- [ ] **Step 1: 给 novelKnowledgePhase.ts 增加共享 phaseBadge**

在 `novelKnowledgePhase.ts` 末尾追加：

```ts
export function phaseBadge(phase: NovelKnowledgePhase): { label: string; className: string } {
    switch (phase) {
        case 'ready':
            return { label: '可检索', className: 'bg-emerald-50 text-emerald-800 border-emerald-200' };
        case 'awaitingSplit':
            return { label: '等待切分', className: 'bg-amber-50 text-amber-900 border-amber-200' };
        case 'awaitingEmbed':
            return { label: '待向量化', className: 'bg-sky-50 text-sky-900 border-sky-200' };
        case 'processing':
            return { label: '处理中', className: 'bg-violet-50 text-violet-900 border-violet-200' };
        case 'failed':
            return { label: '失败', className: 'bg-red-50 text-red-800 border-red-200' };
        default:
            return { label: '未知', className: 'bg-slate-100 text-slate-700 border-slate-200' };
    }
}
```

- [ ] **Step 2: 创建 DeleteNovelModal.tsx**

```tsx
import { useState } from 'react';
import { AlertTriangle, Loader2, Trash2 } from "lucide-react";

interface DeleteNovelModalProps {
  novelName: string;
  isPending: boolean;
  deleteDisabled: boolean;
  onClose: () => void;
  onConfirm: (purgeTerminalSplitTasks: boolean) => void;
}

export function DeleteNovelModal({ novelName, isPending, deleteDisabled, onClose, onConfirm }: DeleteNovelModalProps) {
  const [purge, setPurge] = useState(false);

  return (
    <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4 sm:p-6">
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl p-6">
        <div className="flex items-center gap-3 mb-3">
          <div className="p-2 rounded-xl bg-red-100">
            <AlertTriangle className="w-5 h-5 text-red-600" />
          </div>
          <h2 className="text-lg font-semibold text-slate-900">确认删除知识库</h2>
        </div>
        <p className="text-sm text-slate-600 mb-1">
          将删除 <span className="font-semibold text-slate-800">{novelName}</span> 的所有源文件、切分版本和向量数据，操作不可恢复。
        </p>
        <label className="flex items-start gap-2 mt-4 mb-5 text-xs text-slate-700 cursor-pointer select-none">
          <input
            type="checkbox"
            className="mt-0.5 rounded border-slate-300 text-red-600 focus:ring-red-400 shrink-0"
            checked={purge}
            onChange={(e) => setPurge(e.target.checked)}
          />
          <span>
            同时删除本书流水线任务表中<span className="font-semibold">已成功/已失败</span>的历史记录（进行中的任务仍会阻止删除）
          </span>
        </label>
        <div className="flex justify-end gap-2">
          <button
            onClick={onClose}
            disabled={isPending}
            className="px-4 py-2 rounded-lg bg-white text-slate-600 text-sm font-medium border border-slate-200 hover:bg-slate-50 transition-colors disabled:opacity-50"
          >
            取消
          </button>
          <button
            onClick={() => onConfirm(purge)}
            disabled={isPending || deleteDisabled}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-red-500 text-white text-sm font-semibold hover:bg-red-600 disabled:opacity-60 transition-colors"
          >
            {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
            确认删除
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过（组件暂未被引用，需可编译）。

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Knowledge/components/DeleteNovelModal.tsx \
        novel-splitter-web/src/pages/Knowledge/novelKnowledgePhase.ts
git commit -m "feat(web): 新增 DeleteNovelModal 整书删除弹窗；phaseBadge 提升为共享"
```

### Task 2: NovelTableRow（行 + 可展开版本明细）

**Files:**
- Create: `novel-splitter-web/src/pages/Knowledge/components/NovelTableRow.tsx`

- [ ] **Step 1: 创建组件**

```tsx
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ChevronDown, ChevronRight, FileInput, Loader2, Trash2, AlertCircle } from "lucide-react";
import { knowledgeApi, splitProfileLabel, type SceneSplitProfileDto } from "@/api/knowledgeApi";
import type { NovelSummaryDto, NovelStatRecordDto } from "@/api/novelApi";
import { toast } from 'sonner';
import { format } from 'date-fns';
import { getApiErrorMessage, handleConflict409 } from "@/lib/apiError";
import { novelKnowledgePhase, phaseBadge } from "../novelKnowledgePhase";
import { VersionTag } from "./VersionTag";

interface NovelTableRowProps {
  novel: NovelSummaryDto;
  hasRunningTasks: boolean;
  stats?: NovelStatRecordDto;
  expanded: boolean;
  onToggleExpand: () => void;
  onDelete: (novel: NovelSummaryDto) => void;
}

export function NovelTableRow({ novel, hasRunningTasks, stats, expanded, onToggleExpand, onDelete }: NovelTableRowProps) {
  const queryClient = useQueryClient();
  const phase = novelKnowledgePhase(novel.status);
  const badge = phaseBadge(phase);
  const versionCount = stats ? stats.versions.length : 0;
  const sceneCount = stats?.sceneCount;
  const vectorCount = stats?.vectorCount;

  const { data: splitProfiles, isLoading, isError } = useQuery({
    queryKey: ['splitProfiles', novel.novelId],
    queryFn: () => knowledgeApi.listSplitProfilesByNovelId(novel.novelId),
    enabled: expanded,
  });

  const deleteVersionMutation = useMutation({
    mutationFn: (p: SceneSplitProfileDto) => {
      if (p.chunkSize == null || p.chunkOverlap == null) {
        return Promise.reject(new Error("legacy_missing_chunk"));
      }
      return knowledgeApi.deleteVersionByNovelId(novel.novelId, p.version, p.chunkSize, p.chunkOverlap, false);
    },
    onSuccess: (_, p) => {
      toast.success(`数据集 "${splitProfileLabel(p)}" 已删除`);
      queryClient.invalidateQueries({ queryKey: ['splitProfiles', novel.novelId] });
    },
    onError: (error: any) => {
      if (error?.message === 'legacy_missing_chunk') {
        toast.error('该版本缺少滑窗元数据，无法按分区删除；请重新切分入库或联系管理员处理旧数据。');
        return;
      }
      if (handleConflict409(error, '当前小说存在运行中任务，请等待任务完成后再删除版本')) {
        queryClient.invalidateQueries({ queryKey: ['tasks'] });
        return;
      }
      toast.error(`删除数据集失败: ${getApiErrorMessage(error, '删除失败')}`);
    },
  });

  const updatedText = novel.updatedAt ? format(new Date(novel.updatedAt), 'yyyy-MM-dd HH:mm') : '—';

  return (
    <>
      <tr
        onClick={onToggleExpand}
        className="cursor-pointer transition-colors hover:bg-slate-50/80"
      >
        <td className="px-4 py-3">
          <div className="flex items-center gap-2 min-w-0">
            {expanded ? <ChevronDown className="w-4 h-4 text-slate-400 shrink-0" /> : <ChevronRight className="w-4 h-4 text-slate-400 shrink-0" />}
            <div className="min-w-0">
              <div className="font-medium text-slate-800 truncate">{novel.title || novel.novelId}</div>
              <div className="text-xs font-mono text-slate-400 truncate">{novel.novelId}</div>
            </div>
          </div>
        </td>
        <td className="px-4 py-3">
          <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${badge.className}`}>{badge.label}</span>
          <div className="mt-1 text-xs text-slate-500">{versionCount > 0 ? `${versionCount} 个版本` : '无版本'}</div>
        </td>
        <td className="px-4 py-3 text-xs text-slate-600 tabular-nums">
          {sceneCount != null ? sceneCount.toLocaleString() : '—'}
          {' / '}
          {vectorCount != null ? vectorCount.toLocaleString() : '—'}
        </td>
        <td className="px-4 py-3 text-xs text-slate-500">{updatedText}</td>
        <td className="px-4 py-3">
          <div className="flex items-center justify-end gap-2">
            <Link
              to={`/process?novelId=${encodeURIComponent(novel.novelId)}`}
              onClick={(e) => e.stopPropagation()}
              className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 hover:text-indigo-800 hover:underline"
            >
              <FileInput className="w-3.5 h-3.5" />
              {phase === 'ready' ? '维护' : '去处理'}
            </Link>
            <button
              type="button"
              onClick={(e) => { e.stopPropagation(); onDelete(novel); }}
              disabled={hasRunningTasks}
              className="inline-flex items-center gap-1 p-1 rounded-md text-slate-300 hover:text-red-500 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed"
              title={hasRunningTasks ? "存在运行中任务，暂不可删除" : "删除知识库"}
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={5} className="px-4 pb-4 bg-slate-50/40">
            {isLoading ? (
              <div className="flex justify-center py-3"><Loader2 className="w-4 h-4 animate-spin text-slate-300" /></div>
            ) : isError ? (
              <div className="flex items-center gap-2 text-xs text-red-500 bg-red-50 border border-red-100 px-3 py-2 rounded-lg">
                <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                获取版本列表失败
              </div>
            ) : splitProfiles && splitProfiles.length > 0 ? (
              <div className="flex flex-col gap-2">
                {splitProfiles.map((p) => (
                  <VersionTag
                    key={`${p.version}-${p.chunkSize ?? 'x'}-${p.chunkOverlap ?? 'x'}`}
                    version={splitProfileLabel(p)}
                    onDelete={() => deleteVersionMutation.mutate(p)}
                    isPending={deleteVersionMutation.isPending}
                    disabled={hasRunningTasks || p.chunkSize == null || p.chunkOverlap == null}
                    stat={undefined}
                  />
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic">暂无版本数据</p>
            )}
          </td>
        </tr>
      )}
    </>
  );
}
```

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过（组件暂未被引用）。

注意：`VersionTag` 的 `purgeTerminalSplitTasks`/`onPurgeTerminalSplitTasksChange` props 为可选；此处不传（版本删除不再暴露 purge 勾选，避免在行内堆叠）。若 `noUnusedLocals` 报错或 `format` 无法解析，先核实 `date-fns` 的 `format` 导入路径。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Knowledge/components/NovelTableRow.tsx
git commit -m "feat(web): 新增 NovelTableRow 表格行 + 可展开版本明细"
```

### Task 3: NovelTable（表格）

**Files:**
- Create: `novel-splitter-web/src/pages/Knowledge/components/NovelTable.tsx`

- [ ] **Step 1: 创建组件**

```tsx
import type { NovelSummaryDto, NovelStatRecordDto } from "@/api/novelApi";
import { NovelTableRow } from "./NovelTableRow";

interface NovelTableProps {
  novels: NovelSummaryDto[];
  runningNovelIds: Set<string>;
  statsMap: Map<string, NovelStatRecordDto>;
  expandedNovelId: string | null;
  onToggleExpand: (novelId: string) => void;
  onDelete: (novel: NovelSummaryDto) => void;
}

export function NovelTable({ novels, runningNovelIds, statsMap, expandedNovelId, onToggleExpand, onDelete }: NovelTableProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50/50 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
            <th className="px-4 py-3">小说</th>
            <th className="px-4 py-3 w-28">状态 / 版本</th>
            <th className="px-4 py-3 w-32">场景 / 向量</th>
            <th className="px-4 py-3 w-40">更新时间</th>
            <th className="px-4 py-3 w-32 text-right">操作</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {novels.map((n) => (
            <NovelTableRow
              key={n.novelId}
              novel={n}
              hasRunningTasks={runningNovelIds.has(n.novelId)}
              stats={statsMap.get(n.novelId)}
              expanded={expandedNovelId === n.novelId}
              onToggleExpand={() => onToggleExpand(n.novelId)}
              onDelete={onDelete}
            />
          ))}
          {novels.length === 0 && (
            <tr>
              <td colSpan={5} className="px-4 py-12 text-center text-sm text-slate-400">
                无匹配小说
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 2: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Knowledge/components/NovelTable.tsx
git commit -m "feat(web): 新增 NovelTable 知识库表格"
```

### Task 4: KnowledgePage 重组 + 废弃 NovelVersionsCard

**Files:**
- Modify: `novel-splitter-web/src/pages/KnowledgePage.tsx`（整体替换）
- Delete: `novel-splitter-web/src/pages/Knowledge/components/NovelVersionsCard.tsx`

- [ ] **Step 1: 整体替换 KnowledgePage.tsx**

替换整个文件为（工具栏 + 统计合并 + 表格 + 删除弹窗；删掉分区卡片逻辑）：

```tsx
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AlertCircle, Book, ChevronRight, Database, Loader2, Search } from "lucide-react";
import { novelApi, type NovelSummaryDto, type NovelStatRecordDto } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { taskApi } from "@/api/taskApi";
import { toast } from 'sonner';
import { getApiErrorMessage, handleConflict409 } from "@/lib/apiError";
import { novelKnowledgePhase, KNOWLEDGE_SECTION_ORDER, type NovelKnowledgePhase } from "./Knowledge/novelKnowledgePhase";
import { NovelTable } from "./Knowledge/components/NovelTable";
import { DeleteNovelModal } from "./Knowledge/components/DeleteNovelModal";

type PhaseFilter = 'all' | NovelKnowledgePhase;
type SortKey = 'updated' | 'title' | 'versions';

export default function KnowledgePage() {
  const queryClient = useQueryClient();
  const { data: novels, isLoading, isError } = useQuery({
    queryKey: ['novelSummaries', 'all'],
    queryFn: () => novelApi.getNovelSummaries('all'),
  });
  const { data: tasks = [] } = useQuery({
    queryKey: ['tasks'],
    queryFn: taskApi.getAllTasks,
    refetchInterval: 5000,
  });
  const { data: stats = [] } = useQuery({
    queryKey: ['novelStats'],
    queryFn: novelApi.getNovelStats,
  });

  const [search, setSearch] = useState('');
  const [phaseFilter, setPhaseFilter] = useState<PhaseFilter>('all');
  const [sort, setSort] = useState<SortKey>('updated');
  const [expandedNovelId, setExpandedNovelId] = useState<string | null>(null);
  const [deleteNovelTarget, setDeleteNovelTarget] = useState<NovelSummaryDto | null>(null);

  const novelList = Array.isArray(novels) ? novels : [];
  const runningNovelIds = new Set(
    tasks
      .filter(task => task.status === 'PENDING' || task.status === 'PROCESSING')
      .map(task => task.novelId)
      .filter((novelId): novelId is string => Boolean(novelId))
  );
  const statsMap = useMemo(() => new Map(stats.map((s) => [s.novelId, s])), [stats]);

  const filtered = useMemo(() => {
    let list = novelList;
    const q = search.trim().toLowerCase();
    if (q) {
      list = list.filter((n) => (n.title ?? '').toLowerCase().includes(q) || n.novelId.toLowerCase().includes(q));
    }
    if (phaseFilter !== 'all') {
      list = list.filter((n) => novelKnowledgePhase(n.status) === phaseFilter);
    }
    const sorted = [...list];
    if (sort === 'title') {
      sorted.sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''));
    } else if (sort === 'versions') {
      sorted.sort((a, b) =>
        (statsMap.get(b.novelId)?.versions?.length ?? 0) - (statsMap.get(a.novelId)?.versions?.length ?? 0));
    } else {
      sorted.sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0));
    }
    return sorted;
  }, [novelList, search, phaseFilter, sort, statsMap]);

  const readyCount = novelList.filter((n) => novelKnowledgePhase(n.status) === 'ready').length;
  const nonReadyCount = novelList.length - readyCount;

  const deleteNovelMutation = useMutation({
    mutationFn: ({ novel, purge }: { novel: NovelSummaryDto; purge: boolean }) => (async () => {
      const cleanupTaskId = await knowledgeApi.deleteKnowledgeBaseById(novel.novelId, purge);
      await novelApi.softDeleteNovel(novel.novelId);
      return { novel, cleanupTaskId };
    })(),
    onSuccess: ({ novel, cleanupTaskId }, vars) => {
      const extra = vars.purge ? '；已清理本书终态任务记录' : '';
      toast.success(`知识库 "${novel.title}" 已删除，清理任务：${cleanupTaskId}${extra}`);
      setDeleteNovelTarget(null);
      queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
      if (vars.purge) queryClient.invalidateQueries({ queryKey: ['tasks'] });
    },
    onError: (error: any, vars) => {
      if (handleConflict409(error, '当前小说存在运行中任务，请等待任务完成后再删除知识库/版本')) {
        queryClient.invalidateQueries({ queryKey: ['tasks'] });
        setDeleteNovelTarget(null);
        return;
      }
      toast.error(`删除知识库失败: ${getApiErrorMessage(error, '删除知识库失败')}`);
      setDeleteNovelTarget(null);
    },
  });

  const phaseOptions: { value: PhaseFilter; label: string }[] = [
    { value: 'all', label: '全部状态' },
    ...KNOWLEDGE_SECTION_ORDER.map((p) => ({ value: p, label: sectionTitleForPhase(p) })),
  ];

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="max-w-7xl mx-auto px-6 py-10 space-y-6">

        {/* Page header */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-indigo-100">
                <Database className="w-5 h-5 text-indigo-600" />
              </div>
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">知识库管理</h1>
            </div>
            <Link to="/ingest" className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg shadow-sm hover:bg-indigo-700 transition-colors">
              <Book className="w-4 h-4" />
              + 新增小说
            </Link>
          </div>
          <p className="text-sm text-slate-500 leading-relaxed max-w-3xl">
            支持按<strong className="text-slate-600 font-medium">书名 / novelId 搜索</strong>、按状态过滤与排序。展开行可查看并删除各版本。
            <Link to="/process" className="inline-flex items-center gap-0.5 mx-1 text-indigo-600 font-medium hover:text-indigo-800 hover:underline underline-offset-2 transition-colors">
              场景处理<ChevronRight className="w-3.5 h-3.5" />
            </Link>
            链接可带 novelId 深链。
          </p>
        </div>

        {/* Stats bar */}
        {!isLoading && !isError && novelList.length > 0 && (
          <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white border border-slate-200 text-slate-600 font-medium shadow-sm">
              <Book className="w-3.5 h-3.5 text-indigo-400" />
              共 {novelList.length} 部
            </span>
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-800 font-medium">
              可检索 {readyCount}
            </span>
            {nonReadyCount > 0 && (
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 border border-amber-200 text-amber-900 font-medium">
                待完成流程 {nonReadyCount}
              </span>
            )}
          </div>
        )}

        {/* Toolbar */}
        {!isLoading && !isError && novelList.length > 0 && (
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative flex-1 min-w-[220px]">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400" />
              <input
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="搜索书名或 novelId…"
                className="w-full h-9 pl-9 pr-3 rounded-lg border border-slate-200 bg-white text-sm text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
              />
            </div>
            <select
              value={phaseFilter}
              onChange={(e) => setPhaseFilter(e.target.value as PhaseFilter)}
              className="h-9 rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-400"
            >
              {phaseOptions.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <select
              value={sort}
              onChange={(e) => setSort(e.target.value as SortKey)}
              className="h-9 rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-400"
            >
              <option value="updated">按更新时间</option>
              <option value="title">按标题</option>
              <option value="versions">按版本数</option>
            </select>
            {(search || phaseFilter !== 'all') && (
              <button
                type="button"
                onClick={() => { setSearch(''); setPhaseFilter('all'); }}
                className="h-9 px-3 rounded-lg text-xs font-medium text-indigo-600 border border-indigo-200 bg-indigo-50 hover:bg-indigo-100 transition-colors"
              >
                清空筛选
              </button>
            )}
          </div>
        )}

        {/* Content */}
        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-24 text-slate-400">
            <Loader2 className="w-8 h-8 animate-spin mb-4 text-indigo-400" />
            <p className="text-sm">正在加载知识库…</p>
          </div>
        ) : isError ? (
          <div className="flex items-center gap-4 p-5 rounded-xl border border-red-200 bg-red-50 text-red-700 max-w-lg">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <div>
              <p className="font-semibold text-sm">加载失败</p>
              <p className="text-xs mt-0.5 text-red-500">无法获取小说列表，请确认后端服务已启动。</p>
            </div>
          </div>
        ) : novelList.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-24 text-center">
            <div className="p-4 rounded-2xl bg-slate-100 mb-4">
              <Database className="w-8 h-8 text-slate-400" />
            </div>
            <p className="text-sm font-medium text-slate-600 mb-1">暂无已登记的小说</p>
            <p className="text-xs text-slate-400">
              前往<Link to="/ingest" className="mx-1 text-indigo-500 hover:underline">上传入库</Link>页面上传文件
            </p>
          </div>
        ) : (
          <NovelTable
            novels={filtered}
            runningNovelIds={runningNovelIds}
            statsMap={statsMap}
            expandedNovelId={expandedNovelId}
            onToggleExpand={(id) => setExpandedNovelId((prev) => (prev === id ? null : id))}
            onDelete={(novel) => setDeleteNovelTarget(novel)}
          />
        )}
      </div>

      {/* Delete novel modal */}
      {deleteNovelTarget && (
        <DeleteNovelModal
          key={deleteNovelTarget.novelId}
          novelName={deleteNovelTarget.title ?? deleteNovelTarget.novelId}
          isPending={deleteNovelMutation.isPending}
          deleteDisabled={runningNovelIds.has(deleteNovelTarget.novelId)}
          onClose={() => setDeleteNovelTarget(null)}
          onConfirm={(purge) => deleteNovelMutation.mutate({ novel: deleteNovelTarget, purge })}
        />
      )}
    </div>
  );
}
```

**重要**：上述代码引用了 `sectionTitleForPhase`，需确认它从 `./Knowledge/novelKnowledgePhase` 导出（是——原文件已有）。`KNOWLEDGE_SECTION_ORDER` 保留用于状态过滤下拉顺序。若 `getNovelStats` 的返回类型 `NovelStatRecordDto` 有 `novelId`/`versions`/`sceneCount`/`vectorCount` 字段（是——`novelApi.ts` 中定义），则 `statsMap` 正确。

- [ ] **Step 2: 删除 NovelVersionsCard.tsx**

```bash
git rm novel-splitter-web/src/pages/Knowledge/components/NovelVersionsCard.tsx
```

- [ ] **Step 3: 构建验证**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 通过。修复任何未用 import（如 `accentClassForPhase` 相关、`NovelVersionsCard` 残留引用）。

- [ ] **Step 4: 人工核对**

- `grep -r "NovelVersionsCard" novel-splitter-web/src` → 无引用。
- `grep -r "KNOWLEDGE_SECTION_ORDER" novel-splitter-web/src` → 仅 KnowledgePage 使用（且用于过滤下拉）。

- [ ] **Step 5: Commit**

```bash
git add novel-splitter-web/src/pages/KnowledgePage.tsx
git commit -m "feat(web): /knowledge 页表格化（搜索/过滤/排序 + 可展开版本行），废弃卡片网格"
```

### Task 5: 验证收口

**Files:**
- 无（验证任务）

- [ ] **Step 1: 前端构建**

Run: `cd novel-splitter-web && npm run build`
Expected: tsc + vite 成功。

- [ ] **Step 2: 前端容器重建并重启**

后端未改，仅前端：`docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d --build frontend`
（必须带双 `-f` 保留 host 端口映射——上次教训。）

- [ ] **Step 3: 手工 E2E（覆盖 spec 测试清单）**

浏览器验证 http://localhost:3000/knowledge：

| # | 场景 | 预期 |
|---|---|---|
| 1 | 搜索 | 输入书名片段 / novelId → 表格实时过滤 |
| 2 | 状态过滤 + 排序 | 切状态下拉只剩对应书；切「按标题/按版本数」顺序变化 |
| 3 | 展开版本 | 点行展开显示版本明细；点单个版本「×」内联确认后删除 |
| 4 | 整书删除 | 行尾「删除」→ 弹窗（含 purge 勾选）→ 确认删除成功 + 列表刷新 |
| 5 | 运行中禁用 | 有 PENDING/PROCESSING 任务的书，删除按钮禁用 |
| 6 | 空态 | 搜索无匹配显「无匹配小说」；「清空筛选」恢复 |

- [ ] **Step 4: 无代码提交（E2E 通过即收口）**

---

## Self-Review 结果

- **Spec 覆盖**：搜索/过滤/排序（Task 4 工具栏）、表格 + 可展开版本行（Task 2/3）、整书删除弹窗（Task 1/4）、版本删除（Task 2 展开区 VersionTag）、统计列 `—` 兜底（Task 2/4）、废弃 NovelVersionsCard（Task 4）、空态/错误态（Task 4）。全数覆盖。
- **占位符**：无 TBD/TODO；所有组件完整代码。
- **类型一致性**：`DeleteNovelModal` props（Task 1）与 KnowledgePage 调用（Task 4）一致；`NovelTable` props（Task 3）与 KnowledgePage 传入（Task 4）一致；`NovelTableRow` props（Task 2）与 NovelTable 传入（Task 3）一致；`phaseBadge`（Task 1 导出）在 Task 2 使用。
