import { useState } from 'react';
import { ChevronDown, ChevronUp } from "lucide-react";
import { cn } from "@/lib/utils";
import type { Citation } from "@/types/api";

export function CitationItem({ citation, index }: { citation: Citation; index: number }) {
    const [expanded, setExpanded] = useState(false);
    const colors = [
        { bg: 'bg-purple-50', border: 'border-purple-300', text: 'text-purple-800', score: 'text-purple-400' },
        { bg: 'bg-teal-50',   border: 'border-teal-300',   text: 'text-teal-800',   score: 'text-teal-400'   },
        { bg: 'bg-blue-50',   border: 'border-blue-300',   text: 'text-blue-800',   score: 'text-blue-400'   },
    ];
    const c = colors[index % colors.length];

    return (
        <div
            className={cn(
                "p-3 rounded-lg text-xs border-l-2 cursor-pointer transition-all",
                c.bg, c.border, c.text
            )}
            onClick={() => setExpanded(!expanded)}
        >
            <div className="flex justify-between items-center mb-1">
                <span className="font-semibold flex items-center gap-1">
                    片段 #{index + 1}
                    {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                </span>
                {citation.score && (
                    <span className={cn("font-mono text-[10px]", c.score)}>
                        {citation.score.toFixed(4)}
                    </span>
                )}
            </div>
            <p className={cn("italic leading-relaxed whitespace-pre-wrap", expanded ? "" : "line-clamp-2")}>
                {citation.content || "(无内容)"}
            </p>
        </div>
    );
}