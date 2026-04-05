import { Bot, Sparkles, Database } from "lucide-react";
import { useChatLogic } from "./Chat/hooks/useChatLogic";
import { ChatSidebar } from "./Chat/components/ChatSidebar";
import { MessageList } from "./Chat/components/MessageList";
import { ChatInputArea } from "./Chat/components/ChatInputArea";

export default function ChatPage() {
    const { state, refs, actions } = useChatLogic();

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
                <ChatSidebar state={state} actions={actions} />

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
                        {state.selectedNovel && state.selectedVersion && (
                            <span className="text-xs bg-teal-100 text-teal-700 font-medium px-2.5 py-0.5 rounded-full">
                                {state.selectedNovel} · {state.selectedVersion}
                            </span>
                        )}
                    </div>

                    {/* Messages */}
                    <MessageList 
                        messages={state.messages} 
                        isPending={state.isPending} 
                        bottomRef={refs.messagesEndRef} 
                    />

                    {/* Input area */}
                    <ChatInputArea 
                        inputValue={state.inputValue}
                        isPending={state.isPending}
                        disabled={!state.selectedNovel || state.isPending}
                        placeholder={!state.selectedNovel ? "请先选择小说..." : "输入你的问题，按 Enter 发送..."}
                        actions={actions}
                    />
                </div>
            </div>
        </div>
    );
}