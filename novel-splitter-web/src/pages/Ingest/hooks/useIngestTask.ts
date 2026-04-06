import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { downloadApi } from "@/api/downloadApi";

export function useIngestTask() {
    const queryClient = useQueryClient();

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
    const [ingestStatus, setIngestStatus] = useState<string>("");
    const [isError, setIsError] = useState(false);
    const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

    // Queries
    const { data: tasks = [] } = useQuery({
        queryKey: ['tasks'],
        queryFn: taskApi.getAllTasks,
        refetchInterval: (query) => {
            const list = query.state.data;
            if (!list || list.length === 0) return 10000;
            const hasActive = list.some((t: any) => t.status === 'PENDING' || t.status === 'PROCESSING');
            return hasActive ? 2000 : 10000;
        },
    });

    // Mutations
    const uploadMutation = useMutation({
        mutationFn: novelApi.uploadNovel,
        onSuccess: (data) => {
            const msg = `上传成功！Novel ID: ${data.novelId}`;
            setIngestStatus(msg); 
            setIsError(false);
            toast.success(msg);
            setCurrentNovelId(data.novelId);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg); 
            setIsError(true);
            toast.error(msg);
        },
    });

    const splitMutation = useMutation({
        mutationFn: (novelId: string) => novelApi.splitNovel(novelId, { version, strategy, maxTokens, overlapTokens }),
        onSuccess: (data) => {
            const msg = `切分任务已提交：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
        },
        onError: (error: any) => {
            const msg = `切分失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg);
            setIsError(true);
            toast.error(msg);
        },
    });

    const embedMutation = useMutation({
        mutationFn: (novelId: string) => novelApi.embedNovel(novelId),
        onSuccess: (data) => {
            const msg = `向量化入库任务已提交：${data.message}`;
            setIngestStatus(msg);
            setIsError(false);
            toast.success(msg);
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
            deleteTask: (id: string) => deleteTaskMutation.mutate(id),
        }
    };
}
