import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { chatApi } from "@/api/chatApi";
import type { Citation } from "@/types/api";
import type { NovelSummaryDto } from "@/api/novelApi";

export interface Message {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    citations?: Citation[];
}

export function useChatLogic() {
    const [selectedNovel, setSelectedNovel] = useState<string>(""); // novelId
    const [selectedVersion, setSelectedVersion] = useState<string>("");
    const [topK, setTopK] = useState<number>(3);
    const [inputValue, setInputValue] = useState("");
    const [messages, setMessages] = useState<Message[]>([{
        id: 'welcome',
        role: 'assistant',
        content: '你好！我是 Novel Splitter 助手。请先在左侧选择一本小说，我将根据原文内容为你精准回答问题。',
    }]);

    const messagesEndRef = useRef<HTMLDivElement>(null);

    const { data: novelSummaries } = useQuery({
        queryKey: ['novelSummaries', 'embed_ready'],
        queryFn: () => novelApi.getNovelSummaries('embed_ready'),
    });
    const novels: Array<Pick<NovelSummaryDto, 'novelId' | 'title'>> = novelSummaries?.map(n => ({ novelId: n.novelId, title: n.title })) ?? [];
    const { data: versions } = useQuery({
        queryKey: ['versions', selectedNovel],
        queryFn: () => knowledgeApi.getVersionsByNovelId(selectedNovel),
        enabled: !!selectedNovel,
    });

    useEffect(() => {
        if (novels.length && !selectedNovel) setSelectedNovel(novels[0].novelId);
    }, [novels, selectedNovel]);

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
        chatMutation.mutate({ question: q, novelId: selectedNovel, version: selectedVersion, topK });
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) { 
            e.preventDefault(); 
            handleSend(); 
        }
    };

    return {
        state: { selectedNovel, selectedVersion, topK, inputValue, messages, novels, versions, isPending: chatMutation.isPending },
        refs: { messagesEndRef },
        actions: { setSelectedNovel, setSelectedVersion, setTopK, setInputValue, handleSend, handleKeyDown }
    };
}