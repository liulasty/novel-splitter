import { useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { ListChecks, XCircle } from "lucide-react";
import { cn } from "@/lib/utils";
import { TaskPollerStatus } from '@/pages/Ingest/components/TaskPollerStatus';
import type { ProcessState, ProcessActions } from './ProcessTypes';
import { VersionExperimentPanel } from './VersionExperimentPanel';

interface ProcessingPanelProps {
  state: ProcessState;
  actions: ProcessActions;
}

export function ProcessingPanel({ state, actions }: ProcessingPanelProps) {
  const { currentNovelId, activeTasks, poller } = state;

  const { data: novelOptions = [] } = useQuery({
    queryKey: ['novelSummaries', 'all'],
    queryFn: () => novelApi.getNovelSummaries('all'),
  });

  return (
    <div className="rounded-2xl border-2 border-dashed border-indigo-200 bg-gradient-to-br from-indigo-50/60 via-white to-violet-50/40 p-6 relative">

      {/* Current novel selector（共享） */}
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

      {/* 版本实验视图（创建版本 → 切分 → 向量化 → 激活） */}
      <div className="mb-5">
        <VersionExperimentPanel state={state} actions={actions} />
      </div>

      {/* 全局任务状态 */}
      <TaskPollerStatus tasks={activeTasks} poller={poller} onManualRefresh={actions.manualRefresh} />
    </div>
  );
}
