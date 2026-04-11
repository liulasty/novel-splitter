import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { downloadApi } from "@/api/downloadApi";
import { useTaskPoller } from './useTaskPoller';
import { getApiErrorMessage, handleConflict409, isHttpConflict409 } from '@/lib/apiError';

const LAST_NOVEL_SESSION_KEY = 'ingest:lastNovelId';

export function useIngestTask() {
    const queryClient = useQueryClient();
    const [searchParams, setSearchParams] = useSearchParams();

    // UI State
    const [activeTab, setActiveTab] = useState<'upload' | 'download'>('upload');
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [novelName, setNovelName] = useState<string>("");
    
    // Download State
    const [downloadUrl, setDownloadUrl] = useState("");

    // Split Config
    const [version, setVersion] = useState("v1");
    const [maxTokens, setMaxTokens] = useState(512);
    const [overlapTokens, setOverlapTokens] = useState(64);
    
    // Flow State
    const [currentNovelId, setCurrentNovelId] = useState<string>("");
    const ingestInitRef = useRef(false);
    const [chapterReviewAck, setChapterReviewAck] = useState(false);
    const [chapterTitleRegex, setChapterTitleRegex] = useState('');
    const chapterParseWasRunningRef = useRef(false);

    /** 与 URL、sessionStorage 同步，便于刷新/深链后续切分 */
    const persistCurrentNovelId = useCallback(
        (novelId: string) => {
            const id = novelId.trim();
            setCurrentNovelId(id);
            if (id) {
                try {
                    sessionStorage.setItem(LAST_NOVEL_SESSION_KEY, id);
                } catch {
                    /* ignore quota */
                }
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
        try {
            sessionStorage.removeItem(LAST_NOVEL_SESSION_KEY);
        } catch {
            /* ignore */
        }
        setSearchParams(
            (prev) => {
                const p = new URLSearchParams(prev);
                p.delete('novelId');
                return p;
            },
            { replace: true }
        );
    }, [setSearchParams]);

    /** URL ?novelId= 优先；否则首屏从 sessionStorage 恢复并写回 URL */
    useEffect(() => {
        const fromUrl = searchParams.get('novelId')?.trim();
        if (fromUrl) {
            setCurrentNovelId(fromUrl);
            try {
                sessionStorage.setItem(LAST_NOVEL_SESSION_KEY, fromUrl);
            } catch {
                /* ignore */
            }
            ingestInitRef.current = true;
            return;
        }
        if (!ingestInitRef.current) {
            ingestInitRef.current = true;
            try {
                const fromSession = sessionStorage.getItem(LAST_NOVEL_SESSION_KEY)?.trim();
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
            } catch {
                /* ignore */
            }
        }
    }, [searchParams, setSearchParams]);

    const [ingestStatus, setIngestStatus] = useState<string>("");
    const [isError, setIsError] = useState(false);
    const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

    // Queries
    const { data: tasks = [] } = useQuery({
        queryKey: ['tasks'],
        queryFn: taskApi.getAllTasks,
    });

    // Task Poller
    const { addActiveTask, polledTasks, poller, manualRefresh } = useTaskPoller(tasks, currentNovelId);
    const activeTasks = polledTasks;

    useEffect(() => {
        setChapterReviewAck(false);
    }, [currentNovelId]);

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

    // Mutations
    const uploadMutation = useMutation({
        mutationFn: novelApi.uploadNovel,
        onSuccess: (data) => {
            const msg = `上传成功！Novel ID: ${data.novelId}`;
            setIngestStatus(msg); 
            setIsError(false);
            toast.success(msg);
            persistCurrentNovelId(data.novelId);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg); 
            setIsError(true);
            toast.error(msg);
        },
    });

    /** 章节解析（Load 队列，CHAPTER_PARSE） */
    const chapterParseMutation = useMutation({
        mutationFn: (novelId: string) =>
            novelApi.splitNovel(novelId, {
                version,
                maxScenes: 0,
                ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
            }),
        onSuccess: (data) => {
            const msg = `章节解析已提交：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
        onError: (error: any) => {
            const msg = `章节解析失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    /** 场景切分（Split 队列，SCENE_SPLIT / PIPELINE） */
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
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
        },
        onError: (error: any) => {
            if (isHttpConflict409(error)) {
                const msg = getApiErrorMessage(
                    error,
                    '该小说正在向量化（EMBEDDING），请结束后再发起场景切分，避免与向量化读写冲突。'
                );
                setIngestStatus(`场景切分未提交：${msg}`);
                setIsError(true);
                toast.error(msg);
                return;
            }
            const msg = `场景切分失败：${getApiErrorMessage(error, error.message)}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const embedMutation = useMutation({
        mutationFn: (novelId: string) => novelApi.embedNovel(novelId, version),
        onSuccess: (data) => {
            const msg = `向量化入库任务已提交：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            const msg = `入库失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const downloadAndIngestMutation = useMutation({
        mutationFn: downloadApi.downloadAndIngest,
        onSuccess: (data) => {
            const msg = `下载已登记：${data.message}（当前仅提交章节解析；完成后请再场景切分/向量化）`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            setDownloadUrl("");
            if (data.taskId) addActiveTask(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            const msg = `下载入库失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const deleteTaskMutation = useMutation({
        mutationFn: taskApi.deleteTask,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['tasks'] });
            toast.success("任务记录已删除（章节、场景与向量数据不受影响）");
        },
        onError: (error: any) => {
            if (handleConflict409(error, "任务运行中，暂不可删除，请等待任务完成后重试")) {
                return;
            }
            toast.error(getApiErrorMessage(error, "删除失败"));
        },
    });

    // Handlers
    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files?.[0]) {
            setSelectedFile(e.target.files[0]);
            setNovelName(e.target.files[0].name.replace('.txt', ''));
            setIngestStatus("");
            setIsError(false);
        }
    };

    const handleUpload = () => {
        if (selectedFile) {
            uploadMutation.mutate(selectedFile);
        }
    };

    const handleChapterParse = () => {
        if (!currentNovelId) {
            setIngestStatus("请先上传文件或选择小说");
            setIsError(true);
            return;
        }
        chapterParseMutation.mutate(currentNovelId);
    };

    const handleSceneSplit = (triggerEmbed: boolean) => {
        if (!currentNovelId) {
            setIngestStatus("请先选择小说并完成章节解析");
            setIsError(true);
            return;
        }
        sceneSplitMutation.mutate({ novelId: currentNovelId, triggerEmbed });
    };

    const handleForceReparseChapters = () => {
        if (!currentNovelId) {
            setIngestStatus("请先选择小说");
            setIsError(true);
            return;
        }
        novelApi
            .loadNovel(currentNovelId, {
                force: true,
                version,
                ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
            })
            .then((data) => {
                toast.success(data.message || "强制重解析已提交");
                if (data.taskId) addActiveTask(data.taskId);
                queryClient.invalidateQueries({ queryKey: ['tasks'] });
            })
            .catch((error: any) => {
                toast.error(error.response?.data?.error || error.message || "提交失败");
            });
    };

    const handleEmbed = () => {
        if (!currentNovelId) {
            setIngestStatus("请先完成上传和切分");
            setIsError(true);
            return;
        }
        embedMutation.mutate(currentNovelId);
    };

    const handleDownloadAndIngest = () => {
        if (!downloadUrl || !novelName) {
            setIngestStatus("请填写下载地址和小说名称");
            setIsError(true);
            return;
        }
        downloadAndIngestMutation.mutate({
            url: downloadUrl,
            name: novelName,
            version,
            maxScenes: 0,
            chunkSize: maxTokens,
            chunkOverlap: overlapTokens,
            stages: ['SPLIT', 'EMBED'],
            ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
        });
    };

    return {
        state: {
            activeTab,
            selectedFile,
            novelName,
            downloadUrl,
            version,
            maxTokens,
            overlapTokens,
            currentNovelId,
            chapterReviewAck,
            chapterTitleRegex,
            ingestStatus,
            isError,
            tasks,
            activeTasks,
            poller,
            selectedTaskId,
            isUploading: uploadMutation.isPending,
            isChapterParsing: chapterParseMutation.isPending,
            isSceneSplitting: sceneSplitMutation.isPending,
            isEmbedding: embedMutation.isPending,
            isDownloading: downloadAndIngestMutation.isPending,
        },
        actions: {
            setActiveTab,
            setVersion,
            setMaxTokens,
            setOverlapTokens,
            setNovelName,
            setSelectedTaskId,
            setDownloadUrl,
            setChapterTitleRegex,
            acknowledgeChapterReview: () => setChapterReviewAck(true),
            handleFileChange,
            handleUpload,
            handleChapterParse,
            handleSceneSplit,
            handleForceReparseChapters,
            handleEmbed,
            handleDownloadAndIngest,
            manualRefresh,
            deleteTask: (id: string) => deleteTaskMutation.mutate(id),
            selectNovelById: persistCurrentNovelId,
            clearSelectedNovel: clearCurrentNovelId,
            addActiveTask,
        }
    };
}
