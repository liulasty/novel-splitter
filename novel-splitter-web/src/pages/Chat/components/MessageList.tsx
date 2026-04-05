import { Bot, User, BookOpen, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { CitationItem } from "./CitationItem";
import type { Message } from "../hooks/useChatLogic";

interface MessageListProps {
    messages: Message[];
    isPending: boolean;
    bottomRef: React.RefObject<HTMLDivElement | null>;
}

export function MessageList({ messages, isPending, bottomRef }: MessageListProps) {
    return (
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
            {messages.map((msg) => (
                <div key={msg.id} className={cn("flex gap-3", msg.role === 'user' ? "flex-row-reverse" : "flex-row")}>
                    {/* Avatar */}
                    <div className={cn(
                        "w-8 h-8 rounded-full flex-shrink-0 flex items-center justify-center text-white",
                        msg.role === 'user'
                            ? "bg-gradient-to-br from-blue-500 to-teal-500"
                            : "bg-gradient-to-br from-violet-500 to-purple-600"
                    )}>
                        {msg.role === 'user' ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                    </div>

                    {/* Bubble */}
                    <div className={cn(
                        "max-w-[78%] px-4 py-3 rounded-2xl text-sm leading-relaxed shadow-sm",
                        msg.role === 'user'
                            ? "bg-gradient-to-br from-blue-500 to-violet-500 text-white rounded-tr-sm"
                            : "bg-gray-50 border border-gray-100 text-gray-800 rounded-tl-sm"
                    )}>
                        <p className="whitespace-pre-wrap">{msg.content}</p>

                        {/* Citations */}
                        {msg.citations && msg.citations.length > 0 && (
                            <div className="mt-3 pt-3 border-t border-gray-200/60">
                                <p className="text-xs font-semibold mb-2 flex items-center gap-1 text-gray-500">
                                    <BookOpen className="w-3 h-3" /> 参考片段
                                </p>
                                <div className="space-y-2">
                                    {msg.citations.map((cit, idx) => (
                                        <CitationItem key={idx} citation={cit} index={idx} />
                                    ))}
                                </div>
                            </div>
                        )}
                    </div>
                </div>
            ))}

            {/* Thinking indicator */}
            {isPending && (
                <div className="flex gap-3">
                    <div className="w-8 h-8 rounded-full bg-gradient-to-br from-violet-500 to-purple-600 flex items-center justify-center">
                        <Bot className="w-4 h-4 text-white" />
                    </div>
                    <div className="px-4 py-3 rounded-2xl rounded-tl-sm bg-gray-50 border border-gray-100 flex items-center gap-2">
                        <Loader2 className="w-3.5 h-3.5 animate-spin text-violet-500" />
                        <span className="text-xs text-gray-500">正在思考...</span>
                    </div>
                </div>
            )}
            <div ref={bottomRef} />
        </div>
    );
}