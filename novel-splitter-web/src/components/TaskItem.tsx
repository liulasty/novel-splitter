import { Trash2, ScrollText } from "lucide-react";
import { cn } from "@/lib/utils";
import type { SplitTask } from "@/api/taskApi";

import type { LucideIcon } from "lucide-react";

const STATUS_CONFIG = {
    PENDING:    { label: 'PENDING',    pill: 'bg-gray-100 text-gray-600',    bar: 'bg-gray-400' },
    PROCESSING: { label: 'PROCESSING', pill: 'bg-blue-100 text-blue-700',    bar: 'bg-gradient-to-r from-violet-500 to-blue-500' },
    SUCCESS:    { label: 'SUCCESS',    pill: 'bg-green-100 text-green-700',   bar: 'bg-gradient-to-r from-teal-500 to-green-500' },
    FAILED:     { label: 'FAILED',     pill: 'bg-red-100 text-red-700',       bar: 'bg-red-500' },
} as const;

export function TaskItem({ 
    task, 
    onDelete, 
    onViewLogs,
    Icon 
}: { 
    task: SplitTask, 
    onDelete: (id: string) => void, 
    onViewLogs: (taskId: string) => void,
    Icon: LucideIcon 
}) {
    
    const currentProgress = task.progress;
    const currentMessage = task.message;
    const currentStatus = task.status;
    const taskType = task.taskType || 'SPLIT';
    const canDelete = currentStatus !== 'PENDING' && currentStatus !== 'PROCESSING';
    
    const cfg = STATUS_CONFIG[currentStatus as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.PENDING;

    return (
        <div className="p-4 rounded-xl border border-gray-100 bg-gray-50/60 space-y-2.5">
            {/* Row 1: name + meta */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
                    <Icon className={cn("w-4 h-4 flex-shrink-0", currentStatus === 'PROCESSING' && "animate-spin",
                        currentStatus === 'SUCCESS' ? "text-teal-600" : currentStatus === 'FAILED' ? "text-red-500" : "text-gray-400"
                    )} />
                    {task.novelTitle || task.fileName || task.novelId}
                    <span className={cn(
                        "text-[10px] font-semibold px-1.5 py-0.5 rounded ml-1 tracking-wider uppercase",
                        taskType === 'EMBED' ? "bg-indigo-100 text-indigo-700" : "bg-blue-100 text-blue-700"
                    )}>
                        {taskType === 'EMBED' ? '向量化' : '切分'}
                    </span>
                    {task.version && (
                        <span className="text-[10px] font-semibold bg-gray-200 text-gray-600 px-1.5 py-0.5 rounded ml-1">
                            {task.version}
                        </span>
                    )}
                </div>
                <div className="flex items-center gap-3">
                    <span className="text-xs text-gray-400">{new Date(task.createdAt).toLocaleString()}</span>
                    <button
                        onClick={() => onViewLogs(task.taskId)}
                        className="w-6 h-6 rounded-md bg-violet-50 hover:bg-violet-100 flex items-center justify-center transition-colors"
                        title="查看日志"
                    >
                        <ScrollText className="w-3 h-3 text-violet-500" />
                    </button>
                    <button
                        onClick={() => onDelete(task.taskId)}
                        disabled={!canDelete}
                        className={cn(
                            "w-6 h-6 rounded-md flex items-center justify-center transition-colors",
                            canDelete ? "bg-red-50 hover:bg-red-100" : "bg-gray-100 cursor-not-allowed opacity-60"
                        )}
                        title={canDelete ? "删除任务" : "任务运行中，无法删除"}
                    >
                        <Trash2 className={cn("w-3 h-3", canDelete ? "text-red-500" : "text-gray-400")} />
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

            {/* Stats Details for EMBED/SPLIT */}
            <div className="flex gap-4 text-xs text-gray-500 mt-2">
                {task.sceneCount !== undefined && (
                    <span>总片段: <span className="font-mono text-gray-700 font-medium">{task.sceneCount}</span></span>
                )}
                {task.embeddedCount !== undefined && (
                    <span>已处理: <span className="font-mono text-gray-700 font-medium">{task.embeddedCount}</span></span>
                )}
            </div>

            {/* Message */}
            {currentMessage && (
                <p className="text-xs text-gray-500 font-mono bg-white border border-gray-100 rounded-lg px-3 py-2 mt-2">
                    {currentMessage}
                </p>
            )}
        </div>
    );
}
