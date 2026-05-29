import { useState, useRef, useEffect } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { novelApi } from "@/api/novelApi";
import { knowledgeApi, type SceneSplitProfileDto, splitProfileLabel } from "@/api/knowledgeApi";
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
    /** 选中 {@link splitProfiles} 的下标 */
    const [selectedProfileIndex, setSelectedProfileIndex] = useState<number>(0);
    const [topK, setTopK] = useState<number>(3);
    const [maxScenes, setMaxScenes] = useState<number>(5);
    const [maxContextTokens, setMaxContextTokens] = useState<number>(3000);
    const [maxAnswerTokens, setMaxAnswerTokens] = useState<number>(0);
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
    const novels: Array<Pick<NovelSummaryDto, 'novelId' | 'title' | 'status'>> = novelSummaries?.map(n => ({ novelId: n.novelId, title: n.title, status: n.status })) ?? [];
    const { data: splitProfiles } = useQuery({
        queryKey: ['splitProfiles', selectedNovel],
        queryFn: () => knowledgeApi.listSplitProfilesByNovelId(selectedNovel),
        enabled: !!selectedNovel,
    });

    useEffect(() => {
        if (novels.length && !selectedNovel) setSelectedNovel(novels[0].novelId);
    }, [novels, selectedNovel]);

    useEffect(() => {
        if (splitProfiles?.length) setSelectedProfileIndex(splitProfiles.length - 1);
        else setSelectedProfileIndex(0);
    }, [splitProfiles]);

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
        const profile: SceneSplitProfileDto | undefined = splitProfiles?.[selectedProfileIndex];
        if (!inputValue.trim() || !selectedNovel || !profile?.version) return;
        setMessages(prev => [...prev, { id: Date.now().toString(), role: 'user', content: inputValue }]);
        const q = inputValue;
        setInputValue("");
        chatMutation.mutate({
            question: q,
            novelId: selectedNovel,
            version: profile.version,
            topK,
            chunkSize: profile.chunkSize ?? undefined,
            chunkOverlap: profile.chunkOverlap ?? undefined,
            maxScenes,
            maxContextTokens,
            maxAnswerTokens: maxAnswerTokens > 0 ? maxAnswerTokens : undefined,
        });
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' && !e.shiftKey) { 
            e.preventDefault(); 
            handleSend(); 
        }
    };

    const profileOptions: { index: number; label: string }[] =
        (splitProfiles ?? []).map((p, index) => ({ index, label: splitProfileLabel(p) }));
    const selectedProfileLabel =
        profileOptions.find((o) => o.index === selectedProfileIndex)?.label ?? "";

    return {
        state: {
            selectedNovel,
            selectedProfileIndex,
            selectedProfileLabel,
            topK,
            inputValue,
            maxScenes,
            maxContextTokens,
            maxAnswerTokens,
            messages,
            novels,
            splitProfiles: splitProfiles ?? [],
            profileOptions,
            isPending: chatMutation.isPending,
        },
        refs: { messagesEndRef },
        actions: { setSelectedNovel, setSelectedProfileIndex, setTopK, setMaxScenes, setMaxContextTokens, setMaxAnswerTokens, setInputValue, handleSend, handleKeyDown },
    };
}