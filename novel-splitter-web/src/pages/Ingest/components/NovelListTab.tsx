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
