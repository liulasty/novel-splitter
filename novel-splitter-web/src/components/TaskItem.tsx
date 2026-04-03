import { Trash2, RotateCw } from "lucide-react";
import { useTaskProgress } from "@/hooks/useTaskProgress";
import { cn } from "@/lib/utils";
import { SplitTask } from "@/api/taskApi";
import { novelApi } from "@/api/novelApi";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

const STATUS_CONFIG = {
    PENDING:    { label: 'PENDING',    pill: 'bg-gray-100 text-gray-600',    bar: 'bg-gray-400' },
    PROCESSING: { label: 'PROCESSING', pill: 'bg-blue-100 text-blue-700',    bar: 'bg-gradient-to-r from-violet-500 to-blue-500' },
    SUCCESS:    { label: 'SUCCESS',    pill: 'bg-green-100 text-green-700',   bar: 'bg-gradient-to-r from-teal-500 to-green-500' },
    FAILED:     { label: 'FAILED',     pill: 'bg-red-100 text-red-700',       bar: 'bg-red-500' },
} as const;

export function TaskItem({ task, onDelete, Icon }: { task: SplitTask, onDelete: (id: string) => void, Icon: any }) {
    const queryClient = useQueryClient();
    
    // Only connect to SSE if the task is not yet finished
    const isFinished = task.status === 'SUCCESS' || task.status === 'FAILED';
    const sseTaskId = isFinished ? null : task.taskId;
    
    const progressState = useTaskProgress(sseTaskId);

    // Merge database state with live SSE state
    const currentProgress = isFinished ? task.progress : progressState.progress;
    const currentMessage = isFinished ? task.message : (progressState.message || task.message);
    const currentStatus = isFinished ? task.status : (progressState.status === 'PENDING' ? task.status : progressState.status);
    
    const cfg = STATUS_CONFIG[currentStatus as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.PENDING;

    const retryMutation = useMutation({
        mutationFn: novelApi.ingestNovel,
        onSuccess: (data) => {
            toast.success(`重新入库已触发：${data.message}`);
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
        onError: (error: any) => {
            toast.error(`重试失败：${error.response?.data?.error || error.message}`);
        },
    });

    const handleRetry = () => {
        retryMutation.mutate({ fileName: task.fileName, version: task.version, maxScenes: task.maxScenes });
    };

    return (
        <div className="p-4 rounded-xl border border-gray-100 bg-gray-50/60 space-y-2.5">
            {/* Row 1: name + meta */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
                    <Icon className={cn("w-4 h-4 flex-shrink-0", currentStatus === 'PROCESSING' && "animate-spin",
                        currentStatus === 'SUCCESS' ? "text-teal-600" : currentStatus === 'FAILED' ? "text-red-500" : "text-gray-400"
                    )} />
                    {task.fileName}
                    {task.version && (
                        <span className="text-[10px] font-semibold bg-gray-200 text-gray-600 px-1.5 py-0.5 rounded ml-1">
                            {task.version}
                        </span>
                    )}
                    {task.maxScenes > 0 && task.maxScenes < 2147483647 && (
                        <span className="text-[10px] font-semibold bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">
                            max:{task.maxScenes}
                        </span>
                    )}
                </div>
                <div className="flex items-center gap-3">
                    <span className="text-xs text-gray-400">{new Date(task.createdAt).toLocaleString()}</span>
                    {currentStatus === 'FAILED' && (
                        <button
                            onClick={handleRetry}
                            disabled={retryMutation.isPending}
                            className="w-6 h-6 rounded-md bg-blue-50 hover:bg-blue-100 flex items-center justify-center transition-colors"
                            title="重试"
                        >
                            <RotateCw className={cn("w-3 h-3 text-blue-500", retryMutation.isPending && "animate-spin")} />
                        </button>
                    )}
                    <button
                        onClick={() => onDelete(task.taskId)}
                        className="w-6 h-6 rounded-md bg-red-50 hover:bg-red-100 flex items-center justify-center transition-colors"
                        title="删除任务"
                    >
                        <Trash2 className="w-3 h-3 text-red-500" />
                    </button>
                </div>
            </div>

            {/* Row 2: status + pct */}
            <div className="flex items-center justify-between">
                <span className={cn("text-xs font-semibold px-2.5 py-0.5 rounded-full", cfg.pill)}>
                    {cfg.label}
                </span>
                <span className="text-xs text-gray-400 font-mono">{currentProgress}%</span>
            </div>

            {/* Progress bar */}
            <div className="h-1.5 w-full rounded-full bg-gray-200 overflow-hidden">
                <div className={cn("h-full rounded-full transition-all duration-500", cfg.bar)}
                        style={{ width: `${currentProgress}%` }} />
            </div>

            {/* Message */}
            {currentMessage && (
                <p className="text-xs text-gray-500 font-mono bg-white border border-gray-100 rounded-lg px-3 py-2">
                    {currentMessage}
                </p>
            )}

            {/* Stage History Timeline */}
            {!isFinished && progressState.stageHistory.length > 0 && (
                <div className="mt-3 flex flex-wrap items-center gap-1.5 text-[10px] text-gray-500 font-mono bg-white border border-gray-100 rounded-lg px-3 py-2">
                    {progressState.stageHistory.map((msg, idx) => (
                        <span key={idx} className="flex items-center gap-1">
                            <span className="text-green-500">✓</span> {msg}
                            {idx < progressState.stageHistory.length - 1 && <span className="text-gray-300 ml-1">→</span>}
                        </span>
                    ))}
                    <span className="flex items-center gap-1 ml-1 animate-pulse text-blue-500">...</span>
                </div>
            )}
        </div>
    );
}
