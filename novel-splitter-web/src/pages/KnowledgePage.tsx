import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { AlertCircle, Book, ChevronRight, Database, Loader2, Search } from "lucide-react";
import { novelApi, type NovelSummaryDto, type NovelStatRecordDto } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { taskApi } from "@/api/taskApi";
import { toast } from 'sonner';
import { getApiErrorMessage, handleConflict409 } from "@/lib/apiError";
import { novelKnowledgePhase, KNOWLEDGE_SECTION_ORDER, sectionTitleForPhase, type NovelKnowledgePhase } from "./Knowledge/novelKnowledgePhase";
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
  const statsMap = useMemo(() => {
    const map = new Map<string, NovelStatRecordDto>();
    for (const s of stats) {
      if (s.novelId) map.set(s.novelId, s);
    }
    return map;
  }, [stats]);

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
    onError: (error: any) => {
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
