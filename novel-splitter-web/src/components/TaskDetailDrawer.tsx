import { X, Loader2, AlertCircle, CheckCircle, Clock } from 'lucide-react';
import { useTaskDetail } from '@/hooks/useTaskDetail';
import { cn } from '@/lib/utils';
import { useEffect, useRef } from 'react';

interface TaskDetailDrawerProps {
  taskId: string | null;
  onClose: () => void;
}

const STATUS_CONFIG = {
  PENDING: { label: '等待中', color: 'text-gray-600', bg: 'bg-gray-100', Icon: Clock },
  PROCESSING: { label: '处理中', color: 'text-blue-700', bg: 'bg-blue-100', Icon: Loader2 },
  SUCCESS: { label: '成功', color: 'text-green-700', bg: 'bg-green-100', Icon: CheckCircle },
  FAILED: { label: '失败', color: 'text-red-700', bg: 'bg-red-100', Icon: AlertCircle },
} as const;

export function TaskDetailDrawer({ taskId, onClose }: TaskDetailDrawerProps) {
  const { task, logs, loading, error } = useTaskDetail(taskId);
  const logsEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Auto-scroll to bottom of logs
    if (logsEndRef.current) {
      logsEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  if (!taskId) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      {/* Backdrop */}
      <div 
        className="absolute inset-0 bg-black/20 backdrop-blur-sm transition-opacity"
        onClick={onClose}
      />
      
      {/* Drawer */}
      <div className="relative w-full max-w-md h-full bg-white shadow-2xl flex flex-col animate-in slide-in-from-right duration-300">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-lg font-semibold text-gray-800">任务详情</h2>
          <button 
            onClick={onClose}
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-6 bg-gray-50/50">
          {loading && !task ? (
            <div className="flex flex-col items-center justify-center h-40 text-gray-400">
              <Loader2 className="w-8 h-8 animate-spin mb-3 text-blue-500" />
              <p>正在加载任务信息...</p>
            </div>
          ) : error && !task ? (
            <div className="p-4 bg-red-50 text-red-600 rounded-xl flex items-start gap-3 border border-red-100">
              <AlertCircle className="w-5 h-5 flex-shrink-0 mt-0.5" />
              <p className="text-sm">{error}</p>
            </div>
          ) : task ? (
            <>
              {/* Task Header Info */}
              <div className="bg-white p-5 rounded-xl shadow-sm border border-gray-100 space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-medium text-gray-800 truncate pr-4" title={task.fileName}>
                    {task.fileName}
                  </h3>
                  {(() => {
                    const cfg = STATUS_CONFIG[task.status] || STATUS_CONFIG.PENDING;
                    const Icon = cfg.Icon;
                    return (
                      <span className={cn("inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium whitespace-nowrap", cfg.bg, cfg.color)}>
                        <Icon className={cn("w-3.5 h-3.5", task.status === 'PROCESSING' && "animate-spin")} />
                        {cfg.label}
                      </span>
                    );
                  })()}
                </div>
                
                <div className="grid grid-cols-2 gap-4 text-sm">
                  <div>
                    <span className="text-gray-400 block text-xs mb-1">版本</span>
                    <span className="text-gray-700 font-medium">{task.version || 'v1'}</span>
                  </div>
                  <div>
                    <span className="text-gray-400 block text-xs mb-1">最大场景数</span>
                    <span className="text-gray-700 font-medium">{task.maxScenes > 0 ? task.maxScenes : '不限'}</span>
                  </div>
                  <div className="col-span-2">
                    <span className="text-gray-400 block text-xs mb-1">创建时间</span>
                    <span className="text-gray-700 font-medium">{new Date(task.createdAt).toLocaleString()}</span>
                  </div>
                </div>

                <div className="pt-2 border-t border-gray-50">
                  <div className="flex justify-between items-center mb-1.5 text-xs">
                    <span className="text-gray-500">当前进度</span>
                    <span className="font-medium text-gray-700">{task.progress}%</span>
                  </div>
                  <div className="h-1.5 w-full bg-gray-100 rounded-full overflow-hidden">
                    <div 
                      className={cn(
                        "h-full rounded-full transition-all duration-500",
                        task.status === 'SUCCESS' ? "bg-teal-500" :
                        task.status === 'FAILED' ? "bg-red-500" :
                        "bg-blue-500"
                      )}
                      style={{ width: `${task.progress}%` }}
                    />
                  </div>
                </div>
              </div>

              {/* Logs Terminal */}
              <div className="bg-gray-900 rounded-xl shadow-lg border border-gray-800 overflow-hidden flex flex-col">
                <div className="px-4 py-2 bg-gray-950 border-b border-gray-800 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
                    <div className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                    <div className="w-2.5 h-2.5 rounded-full bg-green-500/80" />
                    <span className="ml-2 text-xs font-medium text-gray-400">运行日志</span>
                  </div>
                  {(task.status === 'PENDING' || task.status === 'PROCESSING') && (
                    <span className="flex h-2 w-2 relative">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                    </span>
                  )}
                </div>
                
                <div className="p-4 font-mono text-xs overflow-y-auto max-h-[400px] min-h-[300px]">
                  {logs.length === 0 ? (
                    <div className="text-gray-600 text-center mt-10">等待日志输出...</div>
                  ) : (
                    <div className="space-y-2">
                      {logs.map((log, i) => (
                        <div key={i} className="flex gap-3 items-start group hover:bg-gray-800/50 rounded px-1 -mx-1">
                          <span className="text-gray-600 flex-shrink-0 select-none">
                            {new Date(log.timestamp).toLocaleTimeString('en-US', { hour12: false })}
                          </span>
                          <span className={cn(
                            "flex-1 break-words",
                            log.status === 'FAILED' ? "text-red-400" :
                            log.status === 'SUCCESS' ? "text-teal-400" :
                            "text-gray-300"
                          )}>
                            <span className="text-blue-400/50 mr-2">[{log.progress}%]</span>
                            {log.message}
                          </span>
                        </div>
                      ))}
                      <div ref={logsEndRef} />
                    </div>
                  )}
                </div>
              </div>
            </>
          ) : null}
        </div>
      </div>
    </div>
  );
}
