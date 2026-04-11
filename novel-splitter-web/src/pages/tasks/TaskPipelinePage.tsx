import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { taskApi } from '@/api/taskApi';
import type { NovelPipelineRequestDto } from '@/api/novelApi';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { getApiErrorMessage } from '@/lib/apiError';

export default function TaskPipelinePage() {
  const [novelId, setNovelId] = useState('');
  const [version, setVersion] = useState('v1');
  const [maxScenes, setMaxScenes] = useState(0);
  const [stages, setStages] = useState<Array<'SPLIT' | 'EMBED'>>(['SPLIT', 'EMBED']);
  const [taskId, setTaskId] = useState<string | null>(null);

  const submit = useMutation({
    mutationFn: () => {
      const body: NovelPipelineRequestDto = {
        stages,
        version: version.trim() || 'v1',
        maxScenes,
      };
      return novelApi.triggerPipeline(novelId.trim(), body);
    },
    onSuccess: (r) => {
      setTaskId(r.taskId);
      toast.success('Pipeline 已提交');
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

  const toggleStage = (s: 'SPLIT' | 'EMBED') => {
    setStages((prev) => (prev.includes(s) ? prev.filter((x) => x !== s) : [...prev, s]));
  };

  return (
    <div className="max-w-2xl mx-auto p-4 space-y-6">
      <Link to="/tasks" className="text-sm text-gray-500 hover:text-gray-800 inline-flex items-center gap-1">
        <ArrowLeft className="w-4 h-4" /> 任务中心
      </Link>
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Pipeline</h1>
        <p className="text-sm text-gray-500 mt-1">
          POST /api/novels/&#123;novelId&#125;/pipeline — stages 含 SPLIT 时仅提交<strong>章节解析</strong>；场景切分请用 /scene-split。
        </p>
      </div>

      <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4 shadow-sm">
        <label className="block text-sm font-medium text-gray-700">novelId</label>
        <input
          className="w-full rounded-lg border border-gray-200 px-3 py-2 font-mono text-sm"
          value={novelId}
          onChange={(e) => setNovelId(e.target.value)}
        />
        <div className="flex gap-4 text-sm">
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={stages.includes('SPLIT')} onChange={() => toggleStage('SPLIT')} />
            SPLIT（章节解析）
          </label>
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={stages.includes('EMBED')} onChange={() => toggleStage('EMBED')} />
            EMBED（向量化）
          </label>
        </div>
        <label className="block text-sm font-medium text-gray-700">version</label>
        <input
          className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
          value={version}
          onChange={(e) => setVersion(e.target.value)}
        />
        <label className="block text-sm font-medium text-gray-700">maxScenes（场景切分请用 /scene-split）</label>
        <input
          type="number"
          className="w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"
          value={maxScenes}
          onChange={(e) => setMaxScenes(Number(e.target.value))}
        />
        <button
          type="button"
          disabled={!novelId.trim() || stages.length === 0 || submit.isPending}
          onClick={() => submit.mutate()}
          className="inline-flex items-center gap-2 rounded-xl bg-emerald-600 text-white px-4 py-2 text-sm font-medium disabled:opacity-50"
        >
          {submit.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
          提交 Pipeline
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
