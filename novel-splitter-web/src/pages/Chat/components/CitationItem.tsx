import { useState } from 'react';
import { ChevronDown, ChevronUp } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Citation } from "@/types/api";

export function CitationItem({ citation, index }: { citation: Citation; index: number }) {
    const [expanded, setExpanded] = useState(false);
    const colors = [
        { bg: 'bg-purple-50', border: 'border-purple-300', text: 'text-purple-800', score: 'text-purple-500' },
        { bg: 'bg-teal-50',   border: 'border-teal-300',   text: 'text-teal-800',   score: 'text-teal-500'   },
        { bg: 'bg-blue-50',   border: 'border-blue-300',   text: 'text-blue-800',   score: 'text-blue-500'   },
    ];
    const c = colors[index % colors.length];

    const chapterTitle = citation.metadata?.chapterTitle || citation.metadata?.chapter_title || citation.metadata?.chapter || "未知章节";
    const confidence = citation.score !== undefined ? ((1 - citation.score) * 100).toFixed(1) + '%' : '--';

    return (
        <div
            className={cn(
                "p-3 rounded-lg text-xs border-l-2 cursor-pointer transition-all",
                c.bg, c.border, c.text
            )}
            onClick={() => setExpanded(!expanded)}
        >
            <div className="flex justify-between items-center mb-1.5">
                <span className="font-semibold flex items-center gap-1.5">
                    片段 #{index + 1}
                    <span className="bg-white/60 px-1.5 py-0.5 rounded font-normal truncate max-w-[120px]" title={chapterTitle}>
                        {chapterTitle}
                    </span>
                    {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                </span>
                {citation.score !== undefined && (
                    <span className={cn("font-mono text-[10px] font-medium", c.score)}>
                        置信度: {confidence}
                    </span>
                )}
            </div>
            <p className={cn("italic leading-relaxed whitespace-pre-wrap", expanded ? "" : "line-clamp-2")}>
                {citation.content || "(无内容)"}
            </p>
        </div>
    );
}