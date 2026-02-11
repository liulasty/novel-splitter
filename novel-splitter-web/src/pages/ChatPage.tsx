import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Send, Bot, User, BookOpen } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { chatApi } from "@/api/chatApi";
import { cn } from "@/lib/utils";
import type { Citation } from "@/types/api";

interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  citations?: Citation[];
}

export default function ChatPage() {
  // --- State ---
  const [selectedNovel, setSelectedNovel] = useState<string>("");
  const [selectedVersion, setSelectedVersion] = useState<string>("");
  const [topK, setTopK] = useState<number>(3);
  const [inputValue, setInputValue] = useState("");
  const [messages, setMessages] = useState<Message[]>([
    {
        id: 'welcome',
        role: 'assistant',
        content: '你好！我是 Novel Splitter 助手。请先在左侧选择一本小说，我将根据原文回答你的问题。'
    }
  ]);

  const messagesEndRef = useRef<HTMLDivElement>(null);

  // --- Queries ---
  const { data: novels } = useQuery({
    queryKey: ['novels'],
    queryFn: novelApi.getNovels,
  });

  const { data: versions } = useQuery({
    queryKey: ['versions', selectedNovel],
    queryFn: () => knowledgeApi.getVersions(selectedNovel),
    enabled: !!selectedNovel,
  });

  // --- Auto-select first options ---
  useEffect(() => {
    if (novels && novels.length > 0 && !selectedNovel) {
        setSelectedNovel(novels[0]);
    }
  }, [novels]);

  useEffect(() => {
    if (versions && versions.length > 0) {
        // Prefer 'v1' or the last one, here just taking the last one as latest usually
        setSelectedVersion(versions[versions.length - 1]);
    } else {
        setSelectedVersion("");
    }
  }, [versions]);

  // --- Scroll to bottom ---
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);


  // --- Mutation ---
  const chatMutation = useMutation({
    mutationFn: chatApi.sendMessage,
    onSuccess: (data) => {
      setMessages(prev => [
        ...prev, 
        { 
            id: Date.now().toString(), 
            role: 'assistant', 
            content: data.answer,
            citations: data.citations
        }
      ]);
    },
    onError: (error) => {
        setMessages(prev => [
            ...prev, 
            { 
                id: Date.now().toString(), 
                role: 'assistant', 
                content: `Error: ${error}`
            }
        ]);
    }
  });

  const handleSend = () => {
    if (!inputValue.trim() || !selectedNovel || !selectedVersion) return;

    const userMsg: Message = {
        id: Date.now().toString(),
        role: 'user',
        content: inputValue
    };

    setMessages(prev => [...prev, userMsg]);
    const currentInput = inputValue;
    setInputValue("");

    chatMutation.mutate({
        question: currentInput,
        novel: selectedNovel,
        version: selectedVersion,
        topK: topK
    });
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          handleSend();
      }
  };

  return (
    <div className="grid gap-6 md:grid-cols-[300px_1fr] h-[calc(100vh-8rem)]">
      {/* Sidebar / Config Area */}
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>会话设置</CardTitle>
            <CardDescription>选择小说版本和检索参数</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
             {/* Novel Select */}
             <div className="space-y-2">
                <label className="text-sm font-medium leading-none">选择小说</label>
                <select 
                    className="flex h-10 w-full items-center justify-between rounded-md border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    value={selectedNovel}
                    onChange={(e) => setSelectedNovel(e.target.value)}
                >
                    <option value="" disabled>-- 请选择 --</option>
                    {Array.isArray(novels) && novels.map(n => <option key={n} value={n}>{n}</option>)}
                </select>
             </div>

             {/* Version Select */}
             <div className="space-y-2">
                <label className="text-sm font-medium leading-none">版本号</label>
                 <select 
                    className="flex h-10 w-full items-center justify-between rounded-md border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    value={selectedVersion}
                    onChange={(e) => setSelectedVersion(e.target.value)}
                    disabled={!selectedNovel || !versions?.length}
                >
                    <option value="" disabled>-- 请选择 --</option>
                    {Array.isArray(versions) && versions.map(v => <option key={v} value={v}>{v}</option>)}
                </select>
             </div>

             {/* TopK Input */}
             <div className="space-y-2">
                <label className="text-sm font-medium leading-none">引用数量 (TopK)</label>
                <input 
                    type="number" 
                    min={1} 
                    max={10}
                    value={topK}
                    onChange={(e) => setTopK(Number(e.target.value))}
                    className="flex h-10 w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm placeholder:text-gray-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                />
             </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Chat Area */}
      <Card className="flex flex-col h-full border-0 shadow-xl bg-white/80 overflow-hidden">
        <div className="flex-1 p-6 overflow-y-auto space-y-6">
           {messages.map((msg) => (
               <div key={msg.id} className={cn("flex w-full", msg.role === 'user' ? "justify-end" : "justify-start")}>
                   <div className={cn(
                       "flex max-w-[80%] gap-3",
                       msg.role === 'user' ? "flex-row-reverse" : "flex-row"
                   )}>
                       {/* Avatar */}
                       <div className={cn(
                           "flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center",
                           msg.role === 'user' ? "bg-blue-600 text-white" : "bg-emerald-600 text-white"
                       )}>
                           {msg.role === 'user' ? <User className="w-5 h-5" /> : <Bot className="w-5 h-5" />}
                       </div>

                       {/* Content Bubble */}
                       <div className={cn(
                           "p-4 rounded-2xl shadow-sm prose prose-sm max-w-none break-words",
                           msg.role === 'user' 
                                ? "bg-blue-600 text-white rounded-tr-none prose-invert" 
                                : "bg-white border border-gray-100 rounded-tl-none text-gray-800"
                       )}>
                           <p className="m-0 whitespace-pre-wrap">{msg.content}</p>

                           {/* Citations */}
                           {msg.citations && msg.citations.length > 0 && (
                               <div className="mt-4 pt-3 border-t border-gray-200/50">
                                   <p className="text-xs font-semibold mb-2 flex items-center gap-1 opacity-70">
                                       <BookOpen className="w-3 h-3" /> 参考片段
                                   </p>
                                   <div className="space-y-2">
                                       {msg.citations.map((cit, idx) => (
                                           <div key={idx} className="bg-gray-50/50 p-2 rounded text-xs text-gray-600 border border-gray-100/50">
                                               <p className="line-clamp-3 italic">"{cit.content}"</p>
                                               {cit.score && <span className="text-[10px] text-gray-400 mt-1 block">Score: {cit.score.toFixed(4)}</span>}
                                           </div>
                                       ))}
                                   </div>
                               </div>
                           )}
                       </div>
                   </div>
               </div>
           ))}
           
           {chatMutation.isPending && (
               <div className="flex justify-start">
                   <div className="flex max-w-[80%] gap-3">
                       <div className="flex-shrink-0 w-8 h-8 rounded-full bg-emerald-600 text-white flex items-center justify-center">
                           <Bot className="w-5 h-5" />
                       </div>
                       <div className="bg-white border border-gray-100 p-4 rounded-2xl rounded-tl-none shadow-sm flex items-center gap-2">
                           <Loader2 className="w-4 h-4 animate-spin text-gray-500" />
                           <span className="text-sm text-gray-500">正在思考...</span>
                       </div>
                   </div>
               </div>
           )}
           <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        <div className="p-4 border-t bg-white/50 backdrop-blur-sm rounded-b-xl">
           <div className="flex gap-2">
              <input 
                type="text" 
                placeholder={!selectedNovel ? "请先选择小说..." : "输入你的问题..."}
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                disabled={!selectedNovel || chatMutation.isPending}
                className="flex h-10 w-full rounded-full border border-gray-200 bg-white px-4 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              />
              <button 
                onClick={handleSend}
                disabled={!selectedNovel || !inputValue.trim() || chatMutation.isPending}
                className="inline-flex items-center justify-center whitespace-nowrap rounded-full text-sm font-medium ring-offset-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-blue-600 text-white hover:bg-blue-700 h-10 px-6 py-2"
              >
                 {chatMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
              </button>
           </div>
        </div>
      </Card>
    </div>
  );
}
