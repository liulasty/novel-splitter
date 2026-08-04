import { useCallback, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi, type SplitTask } from "@/api/taskApi";

interface UseIngestTaskOptions {
  /** 上传成功回调（novelId），用于 IngestPage 切到列表 tab 并定位新卡片 */
  onUploadSuccess?: (novelId: string) => void;
}

export function useIngestTask({ onUploadSuccess }: UseIngestTaskOptions = {}) {
    const queryClient = useQueryClient();

    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [currentNovelId, setCurrentNovelId] = useState<string>("");
    const [ingestStatus, setIngestStatus] = useState<string>("");
    const [isError, setIsError] = useState(false);
    const [strategy, setStrategy] = useState('CN_CHAPTER');
    const [chapterTitleRegex, setChapterTitleRegex] = useState('');
    const [pollingTaskId, setPollingTaskId] = useState('');

    const uploadMutation = useMutation({
        mutationFn: () =>
            novelApi.uploadNovel(selectedFile!, {
                strategy,
                ...(chapterTitleRegex.trim() !== '' ? { chapterTitleRegex: chapterTitleRegex.trim() } : {}),
            }),
        onSuccess: (data) => {
            const msg = `上传成功！章节解析任务已提交`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
            setCurrentNovelId(data.novelId);
            setPollingTaskId(data.taskId);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
            onUploadSuccess?.(data.novelId);
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const { data: polledTask, isError: pollError } = useQuery<SplitTask>({
        queryKey: ['ingestTask', pollingTaskId],
        queryFn: () => taskApi.getTask(pollingTaskId!),
        enabled: !!pollingTaskId,
        retry: false,
        refetchInterval: 2000,
    });

    // 轮询查询失败（任务被清理/服务异常）→ 不能无限卡在解析中，转为失败态。
    useEffect(() => {
        if (!pollError) return;
        setPollingTaskId('');
        setIngestStatus('入库任务状态查询失败，请刷新页面查看实际进度');
        setIsError(true);
    }, [pollError]);

    useEffect(() => {
        const status = polledTask?.status;
        if (!status || (status !== 'SUCCESS' && status !== 'FAILED')) return;
        setPollingTaskId('');
        if (status === 'SUCCESS') {
            setIngestStatus(polledTask.message || '章节解析完成');
            setIsError(false);
            toast.success('章节解析完成');
            queryClient.invalidateQueries({ queryKey: ['chapters', currentNovelId] });
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        } else {
            setIngestStatus('入库失败，已整体回滚，无残留');
            setIsError(true);
            toast.error('入库失败，已整体回滚，无残留');
        }
    }, [polledTask?.status, currentNovelId, queryClient]);

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files?.[0]) {
            setSelectedFile(e.target.files[0]);
            setIngestStatus("");
            setIsError(false);
        }
    };

    const handleUpload = () => {
        if (selectedFile) {
            uploadMutation.mutate();
        }
    };

    const clearSelectedNovel = useCallback(() => setCurrentNovelId(''), []);

    return {
        state: {
            selectedFile,
            currentNovelId,
            ingestStatus,
            isError,
            isUploading: uploadMutation.isPending,
            strategy,
            chapterTitleRegex,
            isPolling: !!pollingTaskId,
            polledTask,
        },
        actions: {
            handleFileChange,
            handleUpload,
            clearSelectedNovel,
            setStrategy,
            setChapterTitleRegex,
        },
    };
}
