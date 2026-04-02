import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Loader2, Send, Bot, User, BookOpen, ChevronDown, ChevronUp, Sparkles, Database } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { chatApi } from "@/api/chatApi";
import { cn } from "@/lib/utils";
import type { Citation } from "@/types/api";

function CitationItem({ citation, index }: { citation: Citation; index: number }) {
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

interface Message {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    citations?: Citation[];
}

export default function ChatPage() {
    const [selectedNovel, setSelectedNovel]   = useState<string>("");
    const [selectedVersion, setSelectedVersion] = useState<string>("");
    const [topK, setTopK]       = useState<number>(3);
    const [inputValue, setInputValue] = useState("");
    const [messages, setMessages] = useState<Message[]>([{
        id: 'welcome',
        role: 'assistant',
        content: '你好！我是 Novel Splitter 助手。请先在左侧选择一本小说，我将根据原文内容为你精准回答问题。',
    }]);

    const messagesEndRef = useRef<HTMLDivElement>(null);

    const { data: novels } = useQuery({ queryKey: ['novels'], queryFn: novelApi.getNovels });
    const { data: versions } = useQuery({
        queryKey: ['versions', selectedNovel],
        queryFn: () => knowledgeApi.getVersions(selectedNovel),
        enabled: !!selectedNovel,
    });

    useEffect(() => {
        if (novels?.length && !selectedNovel) setSelectedNovel(novels[0]);
    }, [novels]);

    useEffect(() => {
        if (versions?.length) setSelectedVersion(versions[versions.length - 1]);
        else setSelectedVersion("");
    }, [versions]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages]);

    const chatMutation = useMutation({
        mutationFn: chatApi.sendMessage,
        onSuccess: (data) => {
            setMessages(prev => [...prev, {
                id: Date.now().toString(),
                role: 'assistant',
                content: data.answer,
                citations: data.citations,
            }]);
        },
        onError: (error) => {
            setMessages(prev => [...prev, {
                id: Date.now().toString(),
                role: 'assistant',
                content: `请求出错：${error}`,
            }]);
        },
    });

    const handleSend = () => {
        if (!inputValue.trim() || !selectedNovel || !selectedVersion) return;
        setMessages(prev => [...prev, { id: Date.now().toString(), role: 'user', content: inputValue }]);
        const q = inputValue;
        setInputValue("");
        chatMutation.mutate({ question: q, novel: selectedNovel, version: selectedVersion, topK });
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); }
    };

    return (
        <div className="flex flex-col gap-4 h-full">
            {/* Page header */}
            <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-violet-600 via-teal-500 to-blue-500 bg-clip-text text-transparent">
                    RAG 智能问答
                </h1>
                <p className="text-sm text-gray-500 mt-1">
          <span className="inline-flex items-center gap-1 bg-violet-100 text-violet-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
            <Database className="w-3 h-3" /> 向量检索
          </span>
                    <span className="inline-flex items-center gap-1 bg-teal-100 text-teal-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
            <Sparkles className="w-3 h-3" /> ChromaDB
          </span>
                    基于知识库向量检索，结合大模型生成精准回答
                </p>
            </div>

            <div className="grid gap-4 md:grid-cols-[280px_1fr] flex-1 min-h-0">
                {/* Sidebar */}
                <div className="flex flex-col gap-3">
                    <div className="rounded-2xl border border-gray-100 overflow-hidden shadow-sm bg-white">
                        {/* Card gradient header */}
                        <div className="px-4 py-3 bg-gradient-to-br from-violet-50 to-blue-50 border-b border-gray-100">
                            <h3 className="text-sm font-semibold text-violet-800">会话设置</h3>
                            <p className="text-xs text-violet-500 mt-0.5">选择小说版本与检索参数</p>
                        </div>
                        <div className="p-4 space-y-4">
                            {/* Novel select */}
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">选择小说</label>
                                <select
                                    className="w-full h-9 rounded-lg border border-gray-200 bg-gray-50 px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-violet-400"
                                    value={selectedNovel}
                                    onChange={(e) => setSelectedNovel(e.target.value)}
                                >
                                    <option value="" disabled>-- 请选择 --</option>
                                    {Array.isArray(novels) && novels.map(n => <option key={n} value={n}>{n}</option>)}
                                </select>
                            </div>

                            {/* Version select */}
                            <div className="space-y-1.5">
                                <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本号</label>
                                <select
                                    className="w-full h-9 rounded-lg border border-gray-200 bg-gray-50 px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-teal-400 disabled:opacity-50"
                                    value={selectedVersion}
                                    onChange={(e) => setSelectedVersion(e.target.value)}
                                    disabled={!selectedNovel || !versions?.length}
                                >
                                    <option value="" disabled>-- 请选择 --</option>
                                    {Array.isArray(versions) && versions.map(v => <option key={v} value={v}>{v}</option>)}
                                </select>
                            </div>

                            {/* TopK */}
                            <div className="space-y-1.5">
                                <div className="flex items-center justify-between">
                                    <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">引用数量 TopK</label>
                                    <span className="w-6 h-6 rounded-full bg-gradient-to-br from-violet-500 to-blue-500 text-white text-xs flex items-center justify-center font-semibold">
                    {topK}
                  </span>
                                </div>
                                <input
                                    type="range" min={1} max={10} value={topK}
                                    onChange={(e) => setTopK(Number(e.target.value))}
                                    className="w-full accent-violet-500"
                                />
                            </div>
                        </div>
                        {/* Status bar */}
                        <div className="px-4 py-2 bg-teal-50 border-t border-teal-100 flex items-center gap-1.5">
                            <span className="w-2 h-2 rounded-full bg-teal-500 animate-pulse" />
                            <span className="text-xs text-teal-700">ChromaDB 已连接</span>
                        </div>
                    </div>
                </div>

                {/* Chat panel */}
                <div className="flex flex-col rounded-2xl border border-gray-100 shadow-sm bg-white overflow-hidden">
                    {/* Chat header */}
                    <div className="flex items-center justify-between px-5 py-3 bg-gradient-to-r from-emerald-50 via-teal-50 to-blue-50 border-b border-gray-100">
                        <div className="flex items-center gap-2.5">
                            <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-violet-500 to-teal-500 flex items-center justify-center">
                                <Bot className="w-4 h-4 text-white" />
                            </div>
                            <span className="font-semibold text-gray-800 text-sm">Novel Splitter 助手</span>
                        </div>
                        {selectedNovel && selectedVersion && (
                            <span className="text-xs bg-teal-100 text-teal-700 font-medium px-2.5 py-0.5 rounded-full">
                {selectedNovel} · {selectedVersion}
              </span>
                        )}
                    </div>

                    {/* Messages */}
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
                        {chatMutation.isPending && (
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
                        <div ref={messagesEndRef} />
                    </div>

                    {/* Input area */}
                    <div className="px-4 py-3 border-t border-gray-100 bg-gray-50/50 flex gap-2 items-center">
                        <input
                            type="text"
                            placeholder={!selectedNovel ? "请先选择小说..." : "输入你的问题，按 Enter 发送..."}
                            value={inputValue}
                            onChange={(e) => setInputValue(e.target.value)}
                            onKeyDown={handleKeyDown}
                            disabled={!selectedNovel || chatMutation.isPending}
                            className="flex-1 h-10 rounded-full border border-gray-200 bg-white px-4 text-sm text-gray-800 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-violet-400 disabled:opacity-50"
                        />
                        <button
                            onClick={handleSend}
                            disabled={!selectedNovel || !inputValue.trim() || chatMutation.isPending}
                            className="w-10 h-10 rounded-full bg-gradient-to-br from-violet-500 to-blue-500 hover:from-violet-600 hover:to-blue-600 text-white flex items-center justify-center transition-all disabled:opacity-40 disabled:pointer-events-none shadow-sm"
                        >
                            {chatMutation.isPending
                                ? <Loader2 className="w-4 h-4 animate-spin" />
                                : <Send className="w-4 h-4" />}
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
}