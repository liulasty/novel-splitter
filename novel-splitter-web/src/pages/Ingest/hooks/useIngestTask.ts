import { useCallback, useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { downloadApi } from "@/api/downloadApi";
import { useTaskPoller } from './useTaskPoller';
import { getApiErrorMessage, handleConflict409 } from '@/lib/apiError';

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
    const [strategy, setStrategy] = useState("semantic");
    const [maxTokens, setMaxTokens] = useState(512);
    const [overlapTokens, setOverlapTokens] = useState(64);
    
    // Flow State
    const [currentNovelId, setCurrentNovelId] = useState<string>("");
    const ingestInitRef = useRef(false);

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

    const splitMutation = useMutation({
        mutationFn: (novelId: string) =>
            novelApi.triggerPipeline(novelId, {
                stages: ['SPLIT'],
                version,
                maxScenes: 0,
            }),
        onSuccess: (data) => {
            const msg = `切分任务已提交：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            if (data.taskId) addActiveTask(data.taskId);
        },
        onError: (error: any) => {
            const msg = `切分失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const embedMutation = useMutation({
        mutationFn: (novelId: string) =>
            novelApi.triggerPipeline(novelId, {
                stages: ['EMBED'],
                version,
            }),
        onSuccess: (data) => {
            const msg = `向量化入库任务已提交：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            if (data.taskId) addActiveTask(data.taskId);
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
            const msg = `下载入库成功：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            setDownloadUrl("");
            if (data.taskId) addActiveTask(data.taskId);
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
            toast.success("任务已删除");
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

    const handleSplit = () => {
        if (!currentNovelId) {
            setIngestStatus("请先上传文件或选择小说");
            setIsError(true);
            return;
        }
        splitMutation.mutate(currentNovelId);
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
            maxScenes: 0 // backward compatibility for download api
        });
    };

    return {
        state: {
            activeTab,
            selectedFile,
            novelName,
            downloadUrl,
            version,
            strategy,
            maxTokens,
            overlapTokens,
            currentNovelId,
            ingestStatus,
            isError,
            tasks,
            activeTasks,
            poller,
            selectedTaskId,
            isUploading: uploadMutation.isPending,
            isSplitting: splitMutation.isPending,
            isEmbedding: embedMutation.isPending,
            isDownloading: downloadAndIngestMutation.isPending,
        },
        actions: {
            setActiveTab,
            setVersion,
            setStrategy,
            setMaxTokens,
            setOverlapTokens,
            setNovelName,
            setSelectedTaskId,
            setDownloadUrl,
            handleFileChange,
            handleUpload,
            handleSplit,
            handleEmbed,
            handleDownloadAndIngest,
            manualRefresh,
            deleteTask: (id: string) => deleteTaskMutation.mutate(id),
            selectNovelById: persistCurrentNovelId,
            clearSelectedNovel: clearCurrentNovelId,
        }
    };
}
