import { useState } from 'react';
import { GitBranch, X, Loader2 } from "lucide-react";

export function VersionTag({
    version,
    onDelete,
    isPending,
}: {
    version: string;
    onDelete: () => void;
    isPending: boolean;
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
        <span className="group/tag inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100 hover:border-indigo-300 hover:bg-indigo-100 transition-all duration-150 cursor-default select-none">
            <GitBranch className="w-3 h-3 shrink-0 text-indigo-400" />
            <span>{version}</span>
            <button
                onClick={() => setConfirming(true)}
                className="ml-0.5 p-0.5 rounded-full text-indigo-300 hover:text-red-500 hover:bg-white opacity-0 group-hover/tag:opacity-100 transition-all duration-150"
                title="删除版本"
            >
                <X className="w-3 h-3" />
            </button>
        </span>
    );
}