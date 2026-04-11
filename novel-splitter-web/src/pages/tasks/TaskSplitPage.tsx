import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { taskApi } from '@/api/taskApi';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/apiError';

export default function TaskSplitPage() {
  const [novelId, setNovelId] = useState('');
  const [version, setVersion] = useState('v1');
  const [maxScenes, setMaxScenes] = useState(0);
  const [taskId, setTaskId] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: () => novelApi.splitNovel(novelId.trim(), { version: version.trim() || 'v1', maxScenes }),
    onSuccess: (r) => {
      setTaskId(r.taskId);
      toast.success('Split 任务已提交（含 Load）');
    },
    onError: (e: unknown) => toast.error(getApiErrorMessage(e, '提交失败')),
  });

  const { data: task } = useQuery({
    queryKey: ['task', taskId],
    queryFn: () => taskApi.getTask(taskId!),
    enabled: !!taskId,
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === 'SUCCESS' || s === 'FAILED' ? false : 2000;
    },
  });

  return (
    <div className="max-w-2xl mx-auto p-4 space-y-6">
      <Link to="/tasks" className="text-sm text-gray-500 hover:text-gray-800 inline-flex items-center gap-1">
        <ArrowLeft className="w-4 h-4" /> 任务中心
      </Link>
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Split（切分）</h1>
        <p className="text-sm text-gray-500 mt-1">POST /api/novels/&#123;novelId&#125;/split — 先 Load 再切场景</p>
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4 shadow-sm">
        <label className="block text-sm font-medium text-gray-700">novelId</label>
        <input
          className="w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm"
          value={novelId}
          onChange={(e) => setNovelId(e.target.value)}
        />
        <label className="block text-sm font-medium text-gray-700">version</label>
        <input
          className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
          value={version}
          onChange={(e) => setVersion(e.target.value)}
        />
        <label className="block text-sm font-medium text-gray-700">maxScenes（0 表示不限制）</label>
        <input
          type="number"
          className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
          value={maxScenes}
          onChange={(e) => setMaxScenes(Number(e.target.value))}
        />
        <button
          type="button"
          disabled={!novelId.trim() || submit.isPending}
          onClick={() => submit.mutate()}
          className="inline-flex items-center gap-2 rounded-xl bg-blue-600 text-white px-4 py-2 text-sm font-medium disabled:opacity-50"
        >
          {submit.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
          提交 Split
        </button>
      </div>

      {taskId && task && (
        <div className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm text-sm text-gray-700">
          <div>任务 {task.taskId}</div>
          <div className="mt-1">
            {task.status} · {task.progress}% · {task.taskType ?? '—'}
          </div>
          <div className="mt-2 text-xs text-gray-500 whitespace-pre-wrap">{task.message}</div>
        </div>
      )}
    </div>
  );
}
