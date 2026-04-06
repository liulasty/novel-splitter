import { useState } from 'react';
import { GitBranch, X, Loader2, Database, AlignLeft } from "lucide-react";
import { NovelStatRecordDto } from "@/api/novelApi";

export function VersionTag({
    version,
    onDelete,
    isPending,
    stat,
}: {
    version: string;
    onDelete: () => void;
    isPending: boolean;
    stat?: NovelStatRecordDto;
}) {
    const [confirming, setConfirming] = useState(false);

    if (confirming) {
        return (
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-600 border border-red-200 animate-in fade-in duration-150">
                <span className="mr-0.5">删除此版本？</span>
                <button
                    onClick={() => { onDelete(); setConfirming(false); }}
                    disabled={isPending}
                    className="px-1.5 py-0.5 rounded bg-red-500 text-white hover:bg-red-600 transition-colors text-[10px] font-semibold"
                >
                    {isPending ? <Loader2 className="w-2.5 h-2.5 animate-spin" /> : '确认'}
                </button>
                <button
                    onClick={() => setConfirming(false)}
                    className="p-0.5 rounded hover:bg-red-100 transition-colors text-red-400 hover:text-red-600"
                >
                    <X className="w-3 h-3" />
                </button>
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
                    className="p-1 rounded-md text-indigo-300 hover:text-red-500 hover:bg-white opacity-0 group-hover/tag:opacity-100 transition-all duration-150"
                    title="删除版本"
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