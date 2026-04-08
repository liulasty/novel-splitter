import { Loader2, AlertTriangle, Clock } from 'lucide-react';
import type { SplitTask } from '@/api/taskApi';

interface TaskPollerStatusProps {
  tasks: SplitTask[];
  poller: {
    errorCount: number;
    isPaused: boolean;
    stuckTaskIds: string[];
    timeoutTaskIds: string[];
  };
  onManualRefresh?: () => Promise<void>;
}

export function TaskPollerStatus({ tasks, poller, onManualRefresh }: TaskPollerStatusProps) {
  if (!tasks || tasks.length === 0) return null;

  return (
    <div className="mt-4 space-y-2">
      {poller.isPaused && (
        <div className="flex items-center justify-between p-3 bg-amber-50 border border-amber-200 rounded-xl">
          <span className="text-xs text-amber-700">
            轮询因连续失败已暂停（{poller.errorCount} 次），请手动刷新。
          </span>
          {onManualRefresh && (
            <button
              onClick={() => void onManualRefresh()}
              className="px-2 py-1 text-xs rounded bg-amber-600 text-white hover:bg-amber-700"
            >
              手动刷新
            </button>
          )}
        </div>
      )}
      {tasks.map(task => {
        const isStuck = poller.stuckTaskIds.includes(task.taskId);
        const isTimeout = poller.timeoutTaskIds.includes(task.taskId);

        return (
          <div key={task.taskId} className="flex items-center justify-between p-3 bg-white border border-gray-200 rounded-xl shadow-sm">
            <div className="flex items-center gap-3">
              <Loader2 className="w-5 h-5 text-blue-500 animate-spin" />
              <div>
                <div className="text-sm font-medium text-gray-800">
                  {task.taskType === 'EMBED' ? '向量化' : '切分'}任务: {task.fileName || task.novelId}
                </div>
                <div className="text-xs text-gray-500">{task.message || '处理中...'}</div>
              </div>
            </div>
            
            <div className="flex items-center gap-4">
              <div className="text-sm font-semibold text-blue-600">
                {task.progress}%
              </div>
              
              {(isTimeout || isStuck) && (
                <div className="flex items-center gap-1.5 px-2 py-1 rounded bg-amber-50 text-amber-700 text-xs font-medium border border-amber-200">
                  {isTimeout ? (
                    <><Clock className="w-3.5 h-3.5" /> 处理超时 {'>'}15m</>
                  ) : (
                    <><AlertTriangle className="w-3.5 h-3.5" /> 进度卡顿 {'>'}5m</>
                  )}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
