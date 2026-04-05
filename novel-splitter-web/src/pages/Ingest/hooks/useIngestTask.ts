import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";

export function useIngestTask() {
    const queryClient = useQueryClient();
    
    // UI State
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploadedFileName, setUploadedFileName] = useState<string>("");
    const [version, setVersion] = useState("v1");
    const [maxScenes, setMaxScenes] = useState(0);
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
            const msg = `上传成功：${data.message}`;
            setIngestStatus(msg); 
            setIsError(false);
            toast.success(msg);
            if (data.fileName) setUploadedFileName(data.fileName);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg); 
            setIsError(true);
            toast.error(msg);
        },
    });

    const ingestMutation = useMutation({
        mutationFn: novelApi.ingestNovel,
        onSuccess: (data) => {
            const msg = `入库成功：${data.message}`;
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
            setUploadedFileName("");
            setIngestStatus("");
            setIsError(false);
        }
    };

    const handleUpload = () => {
        if (selectedFile) {
            uploadMutation.mutate(selectedFile);
        }
    };

    const handleIngest = () => {
        if (!uploadedFileName) {
            setIngestStatus(selectedFile ? "请先点击「上传文件」完成上传" : "请先选择并上传文件");
            setIsError(true);
            return;
        }
        ingestMutation.mutate({ fileName: uploadedFileName, version, maxScenes });
    };

    return {
        state: {
            selectedFile,
            uploadedFileName,
            version,
            maxScenes,
            ingestStatus,
            isError,
            tasks,
            selectedTaskId,
            isUploading: uploadMutation.isPending,
            isIngesting: ingestMutation.isPending,
        },
        actions: {
            setVersion,
            setMaxScenes,
            setSelectedTaskId,
            handleFileChange,
            handleUpload,
            handleIngest,
            deleteTask: (id: string) => deleteTaskMutation.mutate(id),
        }
    };
}
