import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
    UploadCloud, FileText, Loader2, CheckCircle,
    AlertCircle, Clock, Trash2, Zap, Database, GitBranch
} from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { cn } from "@/lib/utils";
import { toast } from 'sonner';

const STATUS_CONFIG = {
    PENDING:    { label: 'PENDING',    pill: 'bg-gray-100 text-gray-600',    bar: 'bg-gray-400',  Icon: Clock },
    PROCESSING: { label: 'PROCESSING', pill: 'bg-blue-100 text-blue-700',    bar: 'bg-gradient-to-r from-violet-500 to-blue-500', Icon: Loader2 },
    SUCCESS:    { label: 'SUCCESS',    pill: 'bg-green-100 text-green-700',   bar: 'bg-gradient-to-r from-teal-500 to-green-500',  Icon: CheckCircle },
    FAILED:     { label: 'FAILED',     pill: 'bg-red-100 text-red-700',       bar: 'bg-red-500',   Icon: AlertCircle },
} as const;

export default function IngestPage() {
    const queryClient = useQueryClient();
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [uploadedFileName, setUploadedFileName] = useState<string>("");
    const [version, setVersion] = useState("v1");
    const [maxScenes, setMaxScenes] = useState(0);
    const [ingestStatus, setIngestStatus] = useState<string>("");
    const [isError, setIsError] = useState(false);

    const { data: tasks = [] } = useQuery({
        queryKey: ['tasks'],
        queryFn: taskApi.getAllTasks,
        refetchInterval: 3000,
    });

    const uploadMutation = useMutation({
        mutationFn: novelApi.uploadNovel,
        onSuccess: (data) => {
            const msg = `上传成功：${data.message}`;
            setIngestStatus(msg); setIsError(false);
            toast.success(msg);
            if (data.fileName) setUploadedFileName(data.fileName);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
        },
        onError: (error: any) => {
            const msg = `上传失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg); setIsError(true);
            toast.error(msg);
        },
    });

    const ingestMutation = useMutation({
        mutationFn: novelApi.ingestNovel,
        onSuccess: (data) => {
            const msg = `入库成功：${data.message}`;
            setIngestStatus(msg); setIsError(false);
            toast.success(msg);
        },
        onError: (error: any) => {
            const msg = `入库失败：${error.response?.data?.error || error.message}`;
            setIngestStatus(msg); setIsError(true);
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

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files?.[0]) {
            setSelectedFile(e.target.files[0]);
            setUploadedFileName("");
            setIngestStatus("");
            setIsError(false);
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

    return (
        <div className="flex flex-col gap-5 max-w-4xl mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-orange-500 via-amber-500 to-violet-600 bg-clip-text text-transparent">
                    入库处理
                </h1>
                <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">
          <span className="inline-flex items-center gap-1 bg-amber-100 text-amber-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
            <Zap className="w-3 h-3" /> RabbitMQ
          </span>
                    <span className="inline-flex items-center gap-1 bg-violet-100 text-violet-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
            <GitBranch className="w-3 h-3" /> 异步队列
          </span>
                    <span className="inline-flex items-center gap-1 bg-teal-100 text-teal-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
            <Database className="w-3 h-3" /> ChromaDB
          </span>
                    上传小说并配置切分参数，由 Worker 异步写入向量知识库
                </p>
            </div>

            {/* Upload card */}
            <div className="rounded-2xl border-2 border-dashed border-amber-200 bg-gradient-to-br from-amber-50/60 via-white to-violet-50/40 p-6">
                {/* Drop zone */}
                <label
                    htmlFor="file-upload"
                    className="flex flex-col items-center justify-center border-2 border-dashed border-amber-300/70 rounded-xl bg-amber-50/50 hover:bg-amber-50 transition-colors cursor-pointer py-8 px-4 text-center mb-5"
                >
                    <div className="w-14 h-14 rounded-full bg-gradient-to-br from-orange-400 to-amber-500 flex items-center justify-center mb-3">
                        <UploadCloud className="w-6 h-6 text-white" />
                    </div>
                    <p className="text-sm font-medium text-gray-700">拖拽文件到此，或点击选择</p>
                    {selectedFile ? (
                        <span className="mt-2 text-xs bg-amber-100 text-amber-800 px-3 py-1 rounded-full font-medium">
              {selectedFile.name}
            </span>
                    ) : (
                        <p className="mt-1 text-xs text-gray-400">支持 .txt 格式，最大 50MB</p>
                    )}
                    <input id="file-upload" type="file" accept=".txt" className="hidden" onChange={handleFileChange} />
                </label>

                {/* Config */}
                <div className="grid grid-cols-2 gap-4 mb-5">
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本号 Version</label>
                        <input
                            type="text" value={version} onChange={(e) => setVersion(e.target.value)}
                            placeholder="v1"
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        />
                    </div>
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">最大场景数（0 = 全部）</label>
                        <input
                            type="number" value={maxScenes} onChange={(e) => setMaxScenes(Number(e.target.value))}
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        />
                    </div>
                </div>

                {/* Actions */}
                <div className="flex gap-3 justify-center">
                    <button
                        onClick={() => selectedFile && uploadMutation.mutate(selectedFile)}
                        disabled={!selectedFile || uploadMutation.isPending}
                        className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
                    >
                        {uploadMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
                        上传文件
                    </button>

                    <button
                        onClick={handleIngest}
                        disabled={!selectedFile || ingestMutation.isPending}
                        className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-blue-500 hover:from-violet-600 hover:to-blue-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                    >
                        {ingestMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                        发送到任务队列
                    </button>
                </div>

                {/* Status */}
                {ingestStatus && (
                    <div className={cn(
                        "mt-4 flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium",
                        isError ? "bg-red-50 text-red-700" : "bg-green-50 text-green-700"
                    )}>
                        {isError ? <AlertCircle className="w-4 h-4 flex-shrink-0" /> : <CheckCircle className="w-4 h-4 flex-shrink-0" />}
                        {ingestStatus}
                    </div>
                )}
            </div>

            {/* Task list */}
            <div className="rounded-2xl border border-gray-100 shadow-sm bg-white overflow-hidden">
                <div className="px-5 py-3.5 bg-gradient-to-r from-violet-50 to-blue-50 border-b border-gray-100 flex items-center justify-between">
                    <h3 className="text-sm font-semibold text-violet-800">入库任务队列</h3>
                    {tasks.length > 0 && (
                        <span className="text-xs bg-violet-100 text-violet-700 font-medium px-2.5 py-0.5 rounded-full">
              {tasks.length} 项任务
            </span>
                    )}
                </div>

                <div className="p-4 space-y-3">
                    {tasks.length === 0 ? (
                        <p className="text-sm text-gray-400 text-center py-6">暂无切分任务</p>
                    ) : tasks.map((task) => {
                        const cfg = STATUS_CONFIG[task.status as keyof typeof STATUS_CONFIG] ?? STATUS_CONFIG.PENDING;
                        const Icon = cfg.Icon;
                        return (
                            <div key={task.taskId} className="p-4 rounded-xl border border-gray-100 bg-gray-50/60 space-y-2.5">
                                {/* Row 1: name + meta */}
                                <div className="flex items-center justify-between">
                                    <div className="flex items-center gap-2 text-sm font-medium text-gray-800">
                                        <Icon className={cn("w-4 h-4 flex-shrink-0", task.status === 'PROCESSING' && "animate-spin",
                                            task.status === 'SUCCESS' ? "text-teal-600" : task.status === 'FAILED' ? "text-red-500" : "text-gray-400"
                                        )} />
                                        {task.fileName}
                                        {task.version && (
                                            <span className="text-[10px] font-semibold bg-gray-200 text-gray-600 px-1.5 py-0.5 rounded ml-1">
                                                {task.version}
                                            </span>
                                        )}
                                        {task.maxScenes > 0 && task.maxScenes < 2147483647 && (
                                            <span className="text-[10px] font-semibold bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded">
                                                max:{task.maxScenes}
                                            </span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <span className="text-xs text-gray-400">{new Date(task.createdAt).toLocaleString()}</span>
                                        <button
                                            onClick={() => deleteTaskMutation.mutate(task.taskId)}
                                            className="w-6 h-6 rounded-md bg-red-50 hover:bg-red-100 flex items-center justify-center transition-colors"
                                            title="删除任务"
                                        >
                                            <Trash2 className="w-3 h-3 text-red-500" />
                                        </button>
                                    </div>
                                </div>

                                {/* Row 2: status + pct */}
                                <div className="flex items-center justify-between">
                  <span className={cn("text-xs font-semibold px-2.5 py-0.5 rounded-full", cfg.pill)}>
                    {cfg.label}
                  </span>
                                    <span className="text-xs text-gray-400 font-mono">{task.progress}%</span>
                                </div>

                                {/* Progress bar */}
                                <div className="h-1.5 w-full rounded-full bg-gray-200 overflow-hidden">
                                    <div className={cn("h-full rounded-full transition-all duration-500", cfg.bar)}
                                         style={{ width: `${task.progress}%` }} />
                                </div>

                                {/* Message */}
                                {task.message && (
                                    <p className="text-xs text-gray-500 font-mono bg-white border border-gray-100 rounded-lg px-3 py-2">
                                        {task.message}
                                    </p>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}