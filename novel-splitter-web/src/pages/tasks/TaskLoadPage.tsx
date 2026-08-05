import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { taskApi } from '@/api/taskApi';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/apiError';

export default function TaskLoadPage() {
  const [novelId, setNovelId] = useState('');
  const [version, setVersion] = useState('v1');
  const [force, setForce] = useState(false);
  const [taskId, setTaskId] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: () => novelApi.loadNovel(novelId.trim(), { version: version.trim() || 'v1', force }),
    onSuccess: (r) => {
      setTaskId(r.taskId);
      toast.success('章节解析任务已提交');
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

  const { data: events = [] } = useQuery({
    queryKey: ['taskEvents', taskId],
    queryFn: () => taskApi.getTaskEvents(taskId!),
    enabled: !!taskId,
    refetchInterval: 3000,
  });

  return (
    <div className="max-w-2xl mx-auto p-4 space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/tasks" className="text-sm text-gray-500 hover:text-gray-800 inline-flex items-center gap-1">
          <ArrowLeft className="w-4 h-4" /> 任务中心
        </Link>
      </div>
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Load（独立解析）</h1>
        <p className="text-sm text-gray-500 mt-1">POST /api/novels/&#123;novelId&#125;/load — 仅生成 chapters + parsed JSON</p>
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4 shadow-sm">
        <label className="block text-sm font-medium text-gray-700">novelId</label>
        <input
          className="w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm"
          value={novelId}
          onChange={(e) => setNovelId(e.target.value)}
          placeholder="上传后返回的 novelId"
        />
        <label className="block text-sm font-medium text-gray-700">version</label>
        <input
          className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
          value={version}
          onChange={(e) => setVersion(e.target.value)}
        />
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input type="checkbox" checked={force} onChange={(e) => setForce(e.target.checked)} />
          强制重解析（忽略已存在完整产物）
        </label>
        <button
          type="button"
          disabled={!novelId.trim() || submit.isPending}
          onClick={() => submit.mutate()}
          className="inline-flex items-center gap-2 rounded-xl bg-indigo-600 text-white px-4 py-2 text-sm font-medium disabled:opacity-50"
        >
          {submit.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
          提交 Load
        </button>
      </div>

      {taskId && task && (
        <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-2 shadow-sm">
          <div className="text-sm font-medium text-gray-800">任务 {task.taskId}</div>
          <div className="text-sm text-gray-600">
            状态: <span className="font-mono">{task.status}</span> · 进度 {task.progress}% · 类型 {task.taskType ?? '—'}
          </div>
          <div className="text-xs text-gray-500 whitespace-pre-wrap">{task.message}</div>
        </div>
      )}

      {taskId && events.length > 0 && (
        <div className="rounded-2xl border border-gray-200 bg-gray-50/80 p-4 max-h-80 overflow-auto">
          <div className="text-xs font-semibold text-gray-600 mb-2">事件</div>
          <ul className="space-y-1 text-xs font-mono text-gray-700">
            {events.slice(-30).map((e, i) => (
              <li key={i}>
                {new Date(e.timestamp).toLocaleTimeString()} [{e.status}] {e.message}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
