import { useState } from 'react';
import { GitBranch, X, Loader2, Database, AlignLeft } from "lucide-react";
import type { NovelStatRecordDto } from "@/api/novelApi";

export function VersionTag({
    version,
    onDelete,
    isPending,
    disabled,
    stat,
    purgeTerminalSplitTasks,
    onPurgeTerminalSplitTasksChange,
}: {
    version: string;
    onDelete: () => void;
    isPending: boolean;
    disabled?: boolean;
    stat?: NovelStatRecordDto;
    purgeTerminalSplitTasks?: boolean;
    onPurgeTerminalSplitTasksChange?: (value: boolean) => void;
}) {
    const [confirming, setConfirming] = useState(false);
    const isDisabled = Boolean(disabled);

    if (confirming) {
        return (
            <span className="inline-flex flex-col gap-1.5 px-2 py-1.5 rounded-lg text-xs font-medium bg-red-50 text-red-600 border border-red-200 animate-in fade-in duration-150">
                <span className="mr-0.5">删除此版本？</span>
                {onPurgeTerminalSplitTasksChange != null && (
                    <label className="flex items-center gap-1.5 font-normal text-[10px] text-red-700 cursor-pointer select-none">
                        <input
                            type="checkbox"
                            className="rounded border-red-300 text-red-600 focus:ring-red-400"
                            checked={Boolean(purgeTerminalSplitTasks)}
                            onChange={(e) => onPurgeTerminalSplitTasksChange(e.target.checked)}
                        />
                        同时清理本书已结束的任务记录（成功/失败）
                    </label>
                )}
                <span className="inline-flex items-center gap-1">
                <button
                    onClick={() => { onDelete(); setConfirming(false); }}
                    disabled={isPending || isDisabled}
                    className="px-1.5 py-0.5 rounded bg-red-500 text-white hover:bg-red-600 transition-colors text-[10px] font-semibold"
                >
                    {isPending ? <Loader2 className="w-2.5 h-2.5 animate-spin" /> : '确认'}
                </button>
                <button
                    onClick={() => setConfirming(false)}
                    disabled={isPending}
                    className="p-0.5 rounded hover:bg-red-100 transition-colors text-red-400 hover:text-red-600 disabled:opacity-60 disabled:cursor-not-allowed"
                >
                    <X className="w-3 h-3" />
                </button>
                </span>
            </span>
        );
    }

    return (
        <div className="flex flex-col gap-1.5 p-2 rounded-lg bg-indigo-50/50 border border-indigo-100/50 hover:bg-indigo-50 transition-colors group/tag">
            <div className="flex items-center justify-between">
                <span className="inline-flex items-center gap-1.5 text-sm font-medium text-indigo-700 select-none">
                    <GitBranch className="w-3.5 h-3.5 shrink-0 text-indigo-400" />
                    <span>{version}</span>
                </span>
                <button
                    onClick={() => setConfirming(true)}
                    disabled={isDisabled}
                    className="p-1 rounded-md text-indigo-300 hover:text-red-500 hover:bg-white opacity-0 group-hover/tag:opacity-100 transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed"
                    title={isDisabled ? "存在运行中任务，暂不可删除" : "删除版本"}
                >
                    <X className="w-3.5 h-3.5" />
                </button>
            </div>
            
            {stat && (
                <div className="flex items-center gap-3 text-[10px] text-slate-500">
                    <span className="inline-flex items-center gap-1 bg-white px-1.5 py-0.5 rounded shadow-sm border border-slate-100" title="段落数 (Scenes)">
                        <AlignLeft className="w-3 h-3 text-slate-400" />
                        {stat.sceneCount.toLocaleString()}
                    </span>
                    <span className="inline-flex items-center gap-1 bg-white px-1.5 py-0.5 rounded shadow-sm border border-slate-100" title="向量数 (Vectors)">
                        <Database className="w-3 h-3 text-indigo-400" />
                        {stat.vectorCount.toLocaleString()}
                    </span>
                </div>
            )}
        </div>
    );
}