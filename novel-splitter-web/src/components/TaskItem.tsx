import { Trash2, RotateCw, FileText, Scissors, Database, ScrollText } from "lucide-react";
import { cn } from "@/lib/utils";
import type { SplitTask } from "@/api/taskApi";
import { novelApi } from "@/api/novelApi";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import type { LucideIcon } from "lucide-react";

const STATUS_CONFIG = {
    PENDING:    { label: 'PENDING',    pill: 'bg-gray-100 text-gray-600',    bar: 'bg-gray-400' },
    PROCESSING: { label: 'PROCESSING', pill: 'bg-blue-100 text-blue-700',    bar: 'bg-gradient-to-r from-violet-500 to-blue-500' },
    SUCCESS:    { label: 'SUCCESS',    pill: 'bg-green-100 text-green-700',   bar: 'bg-gradient-to-r from-teal-500 to-green-500' },
    FAILED:     { label: 'FAILED',     pill: 'bg-red-100 text-red-700',       bar: 'bg-red-500' },
} as const;

const STEPS = [
    { id: 'LOAD', label: '加载文档', icon: FileText, threshold: 15 },
    { id: 'SPLIT', label: '语义切分', icon: Scissors, threshold: 63 },
    { id: 'EMBED', label: '向量入库', icon: Database, threshold: 100 }
];

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
    const queryClient = useQueryClient();
    
    // DB is the single source of truth
    const currentProgress = task.progress;
    const currentMessage = task.message;
    const currentStatus = task.status;
    
    const cfg = STATUS_CONFIG[currentStatus as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.PENDING;

    const normalizedStatus = 
        currentStatus === 'SUCCESS' ? 'SUCCESS' :
        currentStatus === 'FAILED' ? 'FAILED' :
        currentStatus === 'PROCESSING' ? 'RUNNING' : 'PENDING';

    const activeIndex = currentProgress < 15 ? 0 : currentProgress < 63 ? 1 : 2;

    const getStepState = (index: number) => {
        if (normalizedStatus === 'SUCCESS') return 'completed';
        if (normalizedStatus === 'FAILED') {
            if (index < activeIndex) return 'completed';
            if (index === activeIndex) return 'failed';
            return 'pending';
        }
        if (index < activeIndex) return 'completed';
        if (index === activeIndex) return normalizedStatus === 'RUNNING' ? 'loading' : 'pending';
        return 'pending';
    };

    let progressWidth = 0;
    if (normalizedStatus === 'SUCCESS') {
        progressWidth = 100;
    } else {
        if (currentProgress < 15) {
            progressWidth = (currentProgress / 15) * 50;
        } else if (currentProgress < 63) {
            progressWidth = 50 + ((currentProgress - 15) / (63 - 15)) * 50;
        } else {
            progressWidth = 100;
        }
    }

    const retryMutation = useMutation({
        mutationFn: novelApi.ingestNovel,
        onSuccess: (data) => {
            toast.success(`重新入库已触发：${data.message}`);
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
        onError: (error: Error | { response?: { data?: { error?: string } }, message: string }) => {
            const err = error as { response?: { data?: { error?: string } }, message: string };
            toast.error(`重试失败：${err.response?.data?.error || err.message}`);
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
                    <button
                        onClick={() => onViewLogs(task.taskId)}
                        className="w-6 h-6 rounded-md bg-violet-50 hover:bg-violet-100 flex items-center justify-center transition-colors"
                        title="查看日志"
                    >
                        <ScrollText className="w-3 h-3 text-violet-500" />
                    </button>
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

            {/* Stepper UI replacing Stage History Timeline */}
            <div className="mt-8 mb-4 px-6">
                <div className="relative flex items-center justify-between w-full">
                    {/* Background line */}
                    <div className="absolute left-0 top-1/2 -translate-y-1/2 w-full h-[3px] bg-gray-200 rounded-full" />
                    
                    {/* Active line */}
                    <div 
                        className={cn("absolute left-0 top-1/2 -translate-y-1/2 h-[3px] rounded-full transition-all duration-500", cfg.bar)}
                        style={{ width: `${progressWidth}%` }}
                    />

                    {STEPS.map((step, index) => {
                        const state = getStepState(index);
                        const Icon = step.icon;
                        
                        return (
                            <div key={step.id} className="relative z-10 flex flex-col items-center">
                                <div className={cn(
                                    "w-8 h-8 rounded-full flex items-center justify-center border-[2.5px] transition-all duration-500",
                                    state === 'completed' ? "border-teal-500 text-teal-600 bg-teal-50" :
                                    state === 'loading' ? "border-blue-500 text-blue-600 bg-blue-50 shadow-[0_0_0_4px_rgba(59,130,246,0.1)]" :
                                    state === 'failed' ? "border-red-500 text-red-600 bg-red-50" :
                                    "border-gray-200 text-gray-400 bg-white"
                                )}>
                                    {state === 'loading' ? <Icon className="w-4 h-4 animate-pulse" /> : <Icon className="w-4 h-4" />}
                                </div>
                                <span className={cn(
                                    "text-[10px] font-bold absolute -bottom-5 whitespace-nowrap transition-colors duration-500",
                                    state === 'completed' ? "text-teal-600" :
                                    state === 'loading' ? "text-blue-600" :
                                    state === 'failed' ? "text-red-600" :
                                    "text-gray-400"
                                )}>
                                    {step.label}
                                </span>
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}
