import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Activity, ArrowLeft, FilterX } from 'lucide-react';
import { TaskQueueBoard } from './Ingest/components/TaskQueueBoard';
import { taskApi } from '@/api/taskApi';
import { getApiErrorMessage, handleConflict409 } from '@/lib/apiError';
import { cn } from '@/lib/utils';

export default function TasksPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const novelIdFilter = searchParams.get('novelId')?.trim() || '';

  const { data: tasks = [], isLoading } = useQuery({
    queryKey: ['tasks'],
    queryFn: taskApi.getAllTasks,
    refetchInterval: 5000,
  });

  const deleteTaskMutation = useMutation({
    mutationFn: taskApi.deleteTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      toast.success('任务记录已删除（章节、场景与向量数据不受影响）');
    },
    onError: (error: any) => {
      if (handleConflict409(error, '任务运行中，暂不可删除，请等待任务完成后重试')) {
        return;
      }
      toast.error(getApiErrorMessage(error, '删除失败'));
    },
  });

  const filteredTasks = useMemo(() => {
    if (!novelIdFilter) return tasks;
    return tasks.filter(t => t.novelId === novelIdFilter);
  }, [tasks, novelIdFilter]);

  return (
    <div className="flex flex-col gap-5 max-w-4xl mx-auto">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold bg-gradient-to-r from-indigo-600 via-blue-600 to-violet-600 bg-clip-text text-transparent">
            任务监控
          </h1>
          <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">
            监控队列任务进度，查看日志与事件，支持按小说 ID 过滤。
          </p>
          <div className="flex flex-wrap gap-2 mt-3 text-xs">
            <Link to="/tasks/load" className="px-2 py-1 rounded-lg bg-amber-50 text-amber-800 border border-amber-100 hover:bg-amber-100/80">
              Load
            </Link>
            <Link to="/tasks/split" className="px-2 py-1 rounded-lg bg-blue-50 text-blue-800 border border-blue-100 hover:bg-blue-100/80">
              Split
            </Link>
            <Link to="/tasks/embed" className="px-2 py-1 rounded-lg bg-violet-50 text-violet-800 border border-violet-100 hover:bg-violet-100/80">
              Embed
            </Link>
            <Link to="/tasks/pipeline" className="px-2 py-1 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-100 hover:bg-emerald-100/80">
              Pipeline
            </Link>
            <Link to="/tasks/dlq" className="px-2 py-1 rounded-lg bg-rose-50 text-rose-800 border border-rose-100 hover:bg-rose-100/80">
              异常队列
            </Link>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <Link
            to="/ingest"
            className="inline-flex items-center gap-2 h-9 px-4 rounded-full text-sm font-medium text-gray-700 bg-white border border-gray-200 hover:bg-gray-50 transition-colors"
          >
            <ArrowLeft className="w-4 h-4" />
            返回入库
          </Link>
        </div>
      </div>

      {novelIdFilter && (
        <div className="rounded-2xl border border-indigo-100 bg-indigo-50/60 p-4 flex items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm text-indigo-800">
            <Activity className="w-4 h-4" />
            <span className="font-medium">已按 novelId 过滤</span>
            <span className="font-mono text-xs text-indigo-700 bg-white/70 border border-indigo-100 rounded px-2 py-0.5">
              {novelIdFilter}
            </span>
          </div>
          <button
            type="button"
            onClick={() => {
              searchParams.delete('novelId');
              setSearchParams(searchParams);
            }}
            className={cn(
              'inline-flex items-center gap-2 h-8 px-3 rounded-full text-xs font-medium',
              'bg-white border border-indigo-200 text-indigo-700 hover:bg-indigo-50 transition-colors'
            )}
          >
            <FilterX className="w-4 h-4" />
            清除过滤
          </button>
        </div>
      )}

      {isLoading ? (
        <div className="text-sm text-gray-500">正在加载任务...</div>
      ) : (
        <TaskQueueBoard
          tasks={filteredTasks}
          selectedTaskId={selectedTaskId}
          actions={{
            setSelectedTaskId,
            deleteTask: (id: string) => deleteTaskMutation.mutate(id),
          }}
        />
      )}
    </div>
  );
}

