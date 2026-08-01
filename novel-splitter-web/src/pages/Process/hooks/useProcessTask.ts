import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { useTaskPoller } from '@/pages/Ingest/hooks/useTaskPoller';
import { getApiErrorMessage, handleConflict409, isHttpConflict409 } from '@/lib/apiError';
import { useSplitVersion } from '@/hooks/useSplitVersion';

const SESSION_KEY = 'kb:currentNovelId';

export function useProcessTask() {
    const queryClient = useQueryClient();
    const [searchParams, setSearchParams] = useSearchParams();

    const [maxTokens, setMaxTokens] = useState(512);
    const [overlapTokens, setOverlapTokens] = useState(64);
    const [currentNovelId, setCurrentNovelId] = useState<string>("");
    const [chapterReviewAck, setChapterReviewAck] = useState(false);
    const [chapterTitleRegex, setChapterTitleRegex] = useState('');
    const [recognitionStrategy, setRecognitionStrategy] = useState('PLAIN');
    const initRef = useRef(false);

    const { version, setVersion, profiles, currentProfile, refresh: refreshSplitProfiles } =
        useSplitVersion(currentNovelId);

    const persistCurrentNovelId = useCallback(
        (novelId: string) => {
            const id = novelId.trim();
            setCurrentNovelId(id);
            if (id) {
                try { sessionStorage.setItem(SESSION_KEY, id); } catch { /* ignore */ }
                setSearchParams(
                    (prev) => {
                        const p = new URLSearchParams(prev);
                        p.set('novelId', id);
                        return p;
                    },
                    { replace: true }
                );
            }
        },
        [setSearchParams]
    );

    const clearCurrentNovelId = useCallback(() => {
        setCurrentNovelId('');
        try { sessionStorage.removeItem(SESSION_KEY); } catch { /* ignore */ }
        setSearchParams(
            (prev) => {
                const p = new URLSearchParams(prev);
                p.delete('novelId');
                return p;
            },
            { replace: true }
        );
    }, [setSearchParams]);

    useEffect(() => {
        const fromUrl = searchParams.get('novelId')?.trim();
        if (fromUrl) {
            setCurrentNovelId(fromUrl);
            try { sessionStorage.setItem(SESSION_KEY, fromUrl); } catch { /* ignore */ }
            initRef.current = true;
            return;
        }
        if (!initRef.current) {
            initRef.current = true;
            try {
                const fromSession = sessionStorage.getItem(SESSION_KEY)?.trim();
                if (fromSession) {
                    setCurrentNovelId(fromSession);
                    setSearchParams(
                        (prev) => {
                            const p = new URLSearchParams(prev);
                            p.set('novelId', fromSession);
                            return p;
                        },
                        { replace: true }
                    );
                }
            } catch { /* ignore */ }
        }
    }, [searchParams, setSearchParams]);

    useEffect(() => {
        setChapterReviewAck(false);
    }, [currentNovelId]);

    const { data: tasks = [] } = useQuery({
        queryKey: ['tasks'],
        queryFn: taskApi.getAllTasks,
    });

    const { addActiveTask, polledTasks, poller, manualRefresh } = useTaskPoller(tasks, currentNovelId);

    const chapterParseWasRunningRef = useRef(false);
    useEffect(() => {
        if (!currentNovelId) {
            chapterParseWasRunningRef.current = false;
            return;
        }
        const running = tasks.some(
            (t) =>
                t.novelId === currentNovelId &&
                (t.taskType === 'CHAPTER_PARSE' || t.taskType === 'LOAD') &&
                (t.status === 'PENDING' || t.status === 'PROCESSING')
        );
        const wasRunning = chapterParseWasRunningRef.current;
        chapterParseWasRunningRef.current = running;
        if (wasRunning && !running) {
            setChapterReviewAck(false);
        }
    }, [tasks, currentNovelId]);

    const chapterParseMutation = useMutation({
        mutationFn: (novelId: string) =>
            novelApi.splitNovel(novelId, {
                version,
                maxScenes: 0,
                ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
                strategy: recognitionStrategy,
            }),
        onSuccess: (data) => {
            toast.success(`章节解析已提交：${data.message}`);
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
        onError: (error: any) => {
            toast.error(`章节解析失败：${error.response?.data?.error || error.message}`);
        },
    });

    const sceneSplitMutation = useMutation({
        mutationFn: (args: { novelId: string; triggerEmbed: boolean }) =>
            novelApi.sceneSplit(args.novelId, {
                version,
                maxScenes: 0,
                chunkSize: maxTokens,
                chunkOverlap: overlapTokens,
                triggerEmbed: args.triggerEmbed,
            }),
        onSuccess: (data, variables) => {
            const msg = variables.triggerEmbed
                ? `场景切分+向量化已提交：${data.message}`
                : `场景切分已提交：${data.message}`;
            toast.success(msg);
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
        onError: (error: any) => {
            if (isHttpConflict409(error)) {
                toast.error(
                    getApiErrorMessage(
                        error,
                        '该小说正在向量化（EMBEDDING），请结束后再发起场景切分，避免与向量化读写冲突。'
                    )
                );
                return;
            }
            toast.error(`场景切分失败：${getApiErrorMessage(error, error.message)}`);
        },
    });

    const embedMutation = useMutation({
        mutationFn: (novelId: string) => novelApi.embedNovel(novelId, version),
        onSuccess: (data) => {
            toast.success(`向量化入库任务已提交：${data.message}`);
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            toast.error(`入库失败：${error.response?.data?.error || error.message}`);
        },
    });

    const deleteTaskMutation = useMutation({
        mutationFn: taskApi.deleteTask,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
            toast.success('任务记录已删除（章节、场景与向量数据不受影响）');
        },
        onError: (error: any) => {
            if (handleConflict409(error, '任务运行中，暂不可删除，请等待任务完成后重试')) return;
            toast.error(getApiErrorMessage(error, '删除失败'));
        },
    });

    const completedTaskKeysRef = useRef<Set<string>>(new Set());
    useEffect(() => {
        for (const t of tasks) {
            if (t.novelId !== currentNovelId) continue;
            if (t.taskType !== 'SCENE_SPLIT' && t.taskType !== 'EMBED') continue;
            // 仅 SUCCESS 会生成新的版本 profile；挂载时对既有终态任务的刷新有意保留（进入页面即发现最新版本）。
            if (t.status !== 'SUCCESS') continue;
            const key = `${t.taskId}:${t.status}`;
            if (!completedTaskKeysRef.current.has(key)) {
                completedTaskKeysRef.current.add(key);
                refreshSplitProfiles();
            }
        }
    }, [tasks, currentNovelId, refreshSplitProfiles]);

    const handleChapterParse = () => {
        if (!currentNovelId) {
            toast.error('请先选择小说');
            return;
        }
        chapterParseMutation.mutate(currentNovelId);
    };

    const handleSceneSplit = (triggerEmbed: boolean) => {
        if (!currentNovelId) {
            toast.error('请先选择小说并完成章节解析');
            return;
        }
        sceneSplitMutation.mutate({ novelId: currentNovelId, triggerEmbed });
    };

    const handleForceReparseChapters = () => {
        if (!currentNovelId) {
            toast.error('请先选择小说');
            return;
        }
        novelApi
            .loadNovel(currentNovelId, {
                force: true,
                version,
                ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
                strategy: recognitionStrategy,
            })
            .then((data) => {
                toast.success(data.message || '强制重解析已提交');
                if (data.taskId) addActiveTask(data.taskId);
                queryClient.invalidateQueries({ queryKey: ['tasks'] });
            })
            .catch((error: any) => {
                toast.error(error.response?.data?.error || error.message || '提交失败');
            });
    };

    const handleEmbed = () => {
        if (!currentNovelId) {
            toast.error('请先完成上传和切分');
            return;
        }
        embedMutation.mutate(currentNovelId);
    };

    return {
        state: {
            currentNovelId,
            version,
            profiles,
            currentProfile,
            maxTokens,
            overlapTokens,
            chapterReviewAck,
            chapterTitleRegex,
            recognitionStrategy,
            tasks,
            activeTasks: polledTasks,
            poller,
            isChapterParsing: chapterParseMutation.isPending,
            isSceneSplitting: sceneSplitMutation.isPending,
            isEmbedding: embedMutation.isPending,
        },
        actions: {
            setVersion,
            setMaxTokens,
            setOverlapTokens,
            setChapterTitleRegex,
            setRecognitionStrategy,
            acknowledgeChapterReview: () => setChapterReviewAck(true),
            handleChapterParse,
            handleSceneSplit,
            handleForceReparseChapters,
            handleEmbed,
            manualRefresh,
            deleteTask: (id: string) => deleteTaskMutation.mutate(id),
            selectNovelById: persistCurrentNovelId,
            clearSelectedNovel: clearCurrentNovelId,
            addActiveTask,
        },
    };
}
