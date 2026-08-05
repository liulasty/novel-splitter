# 上传入库页 tab 化 + 小说卡片列表 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `/ingest` 页改造为「上传 | 我的小说」双 tab，已上传小说用卡片列表查看、点卡片选中展示章节，并移除 `?novelId=` URL 路由导航。

**Architecture:** `IngestPage` 持 `activeTab` 状态与 tab 切换 UI；`useIngestTask` 去掉 URL/sessionStorage 依赖、暴露上传成功回调；新建自包含的 `NovelListTab` 组件负责卡片网格 + 选中章节详情（复用 `BaselineParsePanel`）。`/process?novelId=` 深链保持不变。

**Tech Stack:** React 19, TypeScript, Vite, TanStack Query, TailwindCSS 4, lucide-react, react-router-dom

**前置说明：** 前端项目无测试框架（仅 `tsc -b && vite build` + `eslint`）。因此每个任务的"验证"步骤为类型检查/构建 + 浏览器手动验证，不写单元测试。

---

## 文件结构

| 文件 | 职责 | 动作 |
|---|---|---|
| `src/pages/Ingest/hooks/useIngestTask.ts` | 上传状态机 + 任务轮询，去掉 URL/sessionStorage | 修改 |
| `src/pages/Ingest/components/NovelListTab.tsx` | 小说卡片网格 + 选中章节详情 | 新建 |
| `src/pages/IngestPage.tsx` | tab 容器：上传 / 我的小说 | 修改 |
| `src/pages/Ingest/components/BaselineParsePanel.tsx` | 章节详情面板（复用，不改） | 只读 |

---

### Task 0: 提交上一轮未提交的竞态修复

工作区 `BaselineParsePanel.tsx` 与 `IngestPage.tsx` 含有上一轮（后端镜像过期诊断时）的竞态修复改动，未提交。先单独提交，与本计划改动隔离。

- [ ] **Step 1: 提交既有改动**

```bash
git add novel-splitter-web/src/pages/Ingest/components/BaselineParsePanel.tsx
git add novel-splitter-web/src/pages/IngestPage.tsx
git commit -m "fix(web): 章节解析完成前不请求章节列表（上传竞态）"
```

- [ ] **Step 2: 确认提交**

Run: `git log --oneline -2`
Expected: 最近两条含 `docs: 上传入库页 tab 化 + 小说卡片列表设计` 与新提交。

---

### Task 1: 改造 useIngestTask（去掉 URL/sessionStorage，暴露上传成功回调）

**Files:**
- Modify: `novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts`（整文件重写）

- [ ] **Step 1: 重写 useIngestTask.ts**

用以下内容整体替换：

```ts
import { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi, type SplitTask } from "@/api/taskApi";

interface UseIngestTaskOptions {
  /** 上传成功回调（novelId），用于 IngestPage 切到列表 tab 并定位新卡片 */
  onUploadSuccess?: (novelId: string) => void;
}

export function useIngestTask({ onUploadSuccess }: UseIngestTaskOptions = {}) {
    const queryClient = useQueryClient();

    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [currentNovelId, setCurrentNovelId] = useState<string>("");
    const [ingestStatus, setIngestStatus] = useState<string>("");
    const [isError, setIsError] = useState(false);
    const [strategy, setStrategy] = useState('CN_CHAPTER');
    const [chapterTitleRegex, setChapterTitleRegex] = useState('');
    const [pollingTaskId, setPollingTaskId] = useState('');

    const uploadMutation = useMutation({
        mutationFn: () =>
            novelApi.uploadNovel(selectedFile!, {
                strategy,
                ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
            }),
        onSuccess: (data) => {
            const msg = `上传成功！章节解析任务已提交`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            setCurrentNovelId(data.novelId);
            setPollingTaskId(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
            onUploadSuccess?.(data.novelId);
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const { data: polledTask, isError: pollError } = useQuery<SplitTask>({
        queryKey: ['ingestTask', pollingTaskId],
        queryFn: () => taskApi.getTask(pollingTaskId!),
        enabled: !!pollingTaskId,
        retry: false,
        refetchInterval: 2000,
    });

    // 轮询查询失败（任务被清理/服务异常）→ 不能无限卡在解析中，转为失败态。
    useEffect(() => {
        if (!pollError) return;
        setPollingTaskId('');
        setIngestStatus('入库任务状态查询失败，请刷新页面查看实际进度');
        setIsError(true);
    }, [pollError]);

    useEffect(() => {
        const status = polledTask?.status;
        if (!status || (status !== 'SUCCESS' && status !== 'FAILED')) return;
        setPollingTaskId('');
        if (status === 'SUCCESS') {
            setIngestStatus(polledTask.message || '章节解析完成');
            setIsError(false);
            toast.success('章节解析完成');
            queryClient.invalidateQueries({ queryKey: ['chapters', currentNovelId] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        } else {
            setIngestStatus('入库失败，已整体回滚，无残留');
            setIsError(true);
            toast.error('入库失败，已整体回滚，无残留');
        }
    }, [polledTask?.status, currentNovelId, queryClient]);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files?.[0]) {
            setSelectedFile(e.target.files[0]);
            setIngestStatus("");
            setIsError(false);
        }
    };

    const handleUpload = () => {
        if (selectedFile) {
            uploadMutation.mutate();
        }
    };

    const clearSelectedNovel = useCallback(() => setCurrentNovelId(''), []);

    return {
        state: {
            selectedFile,
            currentNovelId,
            ingestStatus,
            isError,
            isUploading: uploadMutation.isPending,
            strategy,
            chapterTitleRegex,
            isPolling: !!pollingTaskId,
            polledTask,
        },
        actions: {
            handleFileChange,
            handleUpload,
            clearSelectedNovel,
            setStrategy,
            setChapterTitleRegex,
        },
    };
}
```

要点：删除了 `useSearchParams` / `useRef` import、`SESSION_KEY` 常量、`persistCurrentNovelId`（含 `setSearchParams` 写入）、`clearCurrentNovelId` 的 `sessionStorage.removeItem`、以及从 URL/session 恢复的初始化 `useEffect`。`currentNovelId` 仅内存 state。

- [ ] **Step 2: 验证 `actions` 接口未变**

`UploadPanel` 的 `UploadPanelProps.actions` 类型为 `{ handleFileChange; handleUpload; clearSelectedNovel; setStrategy; setChapterTitleRegex }`。本 Task 返回的 `actions` 键名与签名一致，`UploadPanel.tsx` 无需改动。对照确认后进入下一步。

- [ ] **Step 3: 类型检查**

Run: `cd novel-splitter-web && npx tsc -b`
Expected: 无错误（此时 `IngestPage` 调用 `useIngestTask()` 无参数仍合法，因为 options 参数可选）。

- [ ] **Step 4: Commit**

```bash
git add novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts
git commit -m "refactor(web): useIngestTask 移除 URL/sessionStorage 依赖，暴露上传成功回调"
```

---

### Task 2: 新建 NovelListTab 组件（卡片网格 + 选中章节详情）

**Files:**
- Create: `novel-splitter-web/src/pages/Ingest/components/NovelListTab.tsx`

- [ ] **Step 1: 创建 NovelListTab.tsx**

```tsx
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertCircle, Book, Database, Loader2, RefreshCw } from 'lucide-react';
import { cn } from '@/lib/utils';
import { novelApi, type NovelSummaryDto, type NovelStatRecordDto } from '@/api/novelApi';
import { taskApi } from '@/api/taskApi';
import { BaselineParsePanel } from './BaselineParsePanel';

interface NovelListTabProps {
  /** 上传成功后要定位并选中的新小说 id */
  highlightNovelId?: string;
}

function StatusBadge({ status }: { status: string | null | undefined }) {
    const map: Record<string, { label: string; cls: string }> = {
        RUNNING: { label: '解析中', cls: 'bg-blue-50 text-blue-700 border-blue-200' },
        PARSED: { label: '已解析', cls: 'bg-teal-50 text-teal-700 border-teal-200' },
        COMPLETED: { label: '已完成', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
        FAILED: { label: '失败', cls: 'bg-red-50 text-red-700 border-red-200' },
        EMBEDDING: { label: '向量化中', cls: 'bg-violet-50 text-violet-700 border-violet-200' },
        SPLITTING: { label: '切分中', cls: 'bg-amber-50 text-amber-700 border-amber-200' },
    };
    const cfg = map[status ?? ''] ?? { label: status ?? '未知', cls: 'bg-gray-50 text-gray-600 border-gray-200' };
    return (
        <span className={cn('inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium border shrink-0', cfg.cls)}>
            {cfg.label}
        </span>
    );
}

function formatTime(ts?: number): string {
    if (!ts) return '—';
    const d = new Date(ts);
    return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

export function NovelListTab({ highlightNovelId }: NovelListTabProps) {
    const { data: novels, isLoading, isError, refetch } = useQuery<NovelSummaryDto[]>({
        queryKey: ['novelSummaries', 'all'],
        queryFn: () => novelApi.getNovelSummaries('all'),
    });
    const { data: stats = [] } = useQuery<NovelStatRecordDto[]>({
        queryKey: ['novelStats'],
        queryFn: novelApi.getNovelStats,
    });
    const { data: tasks = [] } = useQuery({
        queryKey: ['tasks'],
        queryFn: taskApi.getAllTasks,
        refetchInterval: 5000,
    });

    const novelList = Array.isArray(novels) ? novels : [];
    const statsMap = useMemo(() => {
        const map = new Map<string, NovelStatRecordDto>();
        for (const s of stats) if (s.novelId) map.set(s.novelId, s);
        return map;
    }, [stats]);
    const runningNovelIds = useMemo(
        () => new Set(
            tasks
                .filter((t) => t.status === 'PENDING' || t.status === 'PROCESSING')
                .map((t) => t.novelId)
                .filter(Boolean)
        ),
        [tasks]
    );

    // 按更新时间倒序；新上传卡片置顶
    const sorted = useMemo(
        () => [...novelList].sort((a, b) => (b.updatedAt ?? 0) - (a.updatedAt ?? 0)),
        [novelList]
    );

    const [selectedNovelId, setSelectedNovelId] = useState<string | null>(highlightNovelId ?? null);

    return (
        <div className="space-y-5">
            {isLoading ? (
                <div className="flex justify-center py-16">
                    <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
                </div>
            ) : isError ? (
                <div className="flex items-center gap-3 px-4 py-3 rounded-xl bg-red-50 border border-red-200 text-sm text-red-700">
                    <AlertCircle className="w-4 h-4 shrink-0" />
                    小说列表加载失败，请确认后端服务已启动。
                    <button
                        type="button"
                        onClick={() => refetch()}
                        className="ml-auto inline-flex items-center gap-1 text-red-600 font-medium hover:underline"
                    >
                        <RefreshCw className="w-3.5 h-3.5" /> 重试
                    </button>
                </div>
            ) : sorted.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-16 text-center">
                    <div className="p-4 rounded-2xl bg-slate-100 mb-4">
                        <Database className="w-8 h-8 text-slate-400" />
                    </div>
                    <p className="text-sm font-medium text-slate-600 mb-1">暂无已上传的小说</p>
                    <p className="text-xs text-slate-400">请先在上传 tab 上传小说文件</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {sorted.map((n) => {
                        const st = statsMap.get(n.novelId);
                        const running = runningNovelIds.has(n.novelId);
                        const selected = selectedNovelId === n.novelId;
                        return (
                            <button
                                key={n.novelId}
                                type="button"
                                onClick={() => setSelectedNovelId(n.novelId)}
                                className={cn(
                                    'text-left rounded-2xl border p-4 transition-all',
                                    selected
                                        ? 'border-indigo-300 bg-indigo-50/60 ring-1 ring-indigo-200'
                                        : 'border-gray-200 bg-white hover:border-indigo-200 hover:bg-white/80'
                                )}
                            >
                                <div className="flex items-start justify-between gap-2">
                                    <div className="min-w-0">
                                        <p className="text-sm font-semibold text-gray-800 truncate">{n.title || n.novelId}</p>
                                        <p className="text-xs text-gray-400 font-mono mt-0.5 truncate">{n.novelId}</p>
                                    </div>
                                    <StatusBadge status={running ? 'RUNNING' : n.status} />
                                </div>
                                <div className="flex items-center gap-3 mt-3 text-xs text-gray-500">
                                    <span className="inline-flex items-center gap-1">
                                        <Book className="w-3.5 h-3.5" />
                                        {st?.sceneCount ?? 0} 场景
                                    </span>
                                    <span>{formatTime(n.updatedAt)}</span>
                                </div>
                            </button>
                        );
                    })}
                </div>
            )}

            {selectedNovelId && (
                <BaselineParsePanel novelId={selectedNovelId} isPolling={runningNovelIds.has(selectedNovelId)} />
            )}
        </div>
    );
}
```

- [ ] **Step 2: 类型检查**

Run: `cd novel-splitter-web && npx tsc -b`
Expected: 无错误。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/Ingest/components/NovelListTab.tsx
git commit -m "feat(web): 新增我的小说列表 tab（卡片网格 + 选中展示章节）"
```

---

### Task 3: IngestPage 加 tab 容器

**Files:**
- Modify: `novel-splitter-web/src/pages/IngestPage.tsx`（整文件重写）

- [ ] **Step 1: 重写 IngestPage.tsx**

用以下内容整体替换：

```tsx
import { useState } from "react";
import { Link } from "react-router-dom";
import { FileInput, List } from "lucide-react";
import { cn } from "@/lib/utils";
import { useIngestTask } from "./Ingest/hooks/useIngestTask";
import { UploadPanel } from "./Ingest/components/UploadPanel";
import { NovelListTab } from "./Ingest/components/NovelListTab";

type TabKey = 'upload' | 'novels';

export default function IngestPage() {
    const [activeTab, setActiveTab] = useState<TabKey>('upload');
    const [highlightNovelId, setHighlightNovelId] = useState<string | undefined>(undefined);
    const { state, actions } = useIngestTask({
        onUploadSuccess: (novelId) => {
            setHighlightNovelId(novelId);
            setActiveTab('novels');
        },
    });

    const tabs: { key: TabKey; label: string; icon: typeof FileInput }[] = [
        { key: 'upload', label: '上传', icon: FileInput },
        { key: 'novels', label: '我的小说', icon: List },
    ];

    return (
        <div className="flex flex-col gap-5 max-w-4xl mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-orange-500 via-amber-500 to-violet-600 bg-clip-text text-transparent">
                    上传入库
                </h1>
                <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">
                    上传小说文件到知识库。上传完成后，请前往「<Link to="/process" className="text-indigo-600 font-medium hover:underline">场景处理</Link>」
                    页面进行章节解析、场景切分与向量化入库。
                </p>
            </div>

            {/* Tab bar */}
            <div className="flex gap-1 p-1 rounded-full bg-gray-100 w-fit">
                {tabs.map((t) => {
                    const Icon = t.icon;
                    const isActive = activeTab === t.key;
                    return (
                        <button
                            key={t.key}
                            type="button"
                            onClick={() => setActiveTab(t.key)}
                            className={cn(
                                "inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-medium transition-all",
                                isActive
                                    ? "bg-white text-indigo-600 shadow-sm ring-1 ring-gray-200"
                                    : "text-gray-500 hover:text-gray-700"
                            )}
                        >
                            <Icon className="w-4 h-4" />
                            {t.label}
                        </button>
                    );
                })}
            </div>

            {activeTab === 'upload' ? (
                <UploadPanel state={state} actions={actions} />
            ) : (
                <NovelListTab highlightNovelId={highlightNovelId} />
            )}
        </div>
    );
}
```

- [ ] **Step 2: 类型检查**

Run: `cd novel-splitter-web && npx tsc -b`
Expected: 无错误。

- [ ] **Step 3: Commit**

```bash
git add novel-splitter-web/src/pages/IngestPage.tsx
git commit -m "feat(web): /ingest 页 tab 化（上传 | 我的小说），移除 URL 路由导航"
```

---

### Task 4: 构建验证

**Files:** 无（验证）

- [ ] **Step 1: 全量构建**

Run: `cd novel-splitter-web && npm run build`
Expected: `tsc -b && vite build` 通过，输出 `✓ built in <N>s`。

- [ ] **Step 2: 运行 lint**

Run: `cd novel-splitter-web && npm run lint`
Expected: 无错误（如有警告需确认与本次改动无关）。

---

### Task 5: 部署前端 + 浏览器验证

**Files:** 无（部署/验证）

- [ ] **Step 1: 重新构建并部署前端镜像**

Run:
```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev build frontend
docker compose -f docker-compose.yml -f docker-compose.dev.yml --env-file config/.env.dev up -d frontend
```
Expected: frontend 镜像重建、容器 Recreated + Started。

- [ ] **Step 2: 浏览器手动验证（关键路径）**

在 `http://localhost:3000/ingest` 验证：

1. 默认落在「上传」tab；顶部可见 `[上传] [我的小说]` 两个 tab。
2. 上传一个 `.txt` 小说 → 自动切到「我的小说」tab，新卡片置顶、高亮选中，下方显示章节列表。
3. 上传后章节仍在解析时，卡片显示「解析中」徽标，章节区显示「章节解析中，完成后自动展示章节目录…」。
4. 解析完成后章节列表自动刷新显示（无 500 报错）。
5. 刷新页面 → 回到「上传」tab。
6. 点击其他卡片 → 章节区切换为该书章节。
7. 章节详情内「前往场景处理」链接可跳转 `/process?novelId=xxx`。
8. URL 保持 `/ingest`，不再出现 `?novelId=`。

- [ ] **Step 3: 回归确认**

在 `http://localhost:3000/knowledge` 确认知识库列表、删除功能不受影响；`/process?novelId=xxx` 深链仍可用。

---

## Self-Review 记录

- **Spec 覆盖**：设计文档的 7 项需求决策均有对应 Task（tab 位置→T3；上传后自动切换→T1 回调+T3；章节展示位置→T2 复用 BaselineParsePanel；URL 移除→T1 删 URL 逻辑；刷新回上传 tab→T3 默认 state；旧链接忽略→T1 删 URL 读取；新建组件→T2）。
- **占位符扫描**：所有 Task 均含完整代码与命令，无 TBD/TODO。
- **类型一致性**：`useIngestTask` 返回的 `state`/`actions` 与 `UploadPanel` props 接口逐键对应；`NovelListTab` 的 `highlightNovelId` prop 与 T3 传入一致；`BaselineParsePanel` 的 `novelId`/`isPolling` prop 与 T2 使用一致。
