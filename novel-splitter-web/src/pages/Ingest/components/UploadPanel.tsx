import { UploadCloud, FileText, Loader2, CheckCircle, AlertCircle, DownloadCloud, Eye } from "lucide-react";
import { cn } from "@/lib/utils";
import { useState } from 'react';
import { SplitPreviewModal } from './SplitPreviewModal';
import { TaskPollerStatus } from './TaskPollerStatus';
import type { SplitTask } from "@/api/taskApi";

interface UploadPanelProps {
    state: {
        activeTab: 'upload' | 'download';
        selectedFile: File | null;
        novelName: string;
        downloadUrl: string;
        version: string;
        strategy: string;
        maxTokens: number;
        overlapTokens: number;
        currentNovelId: string;
        ingestStatus: string;
        isError: boolean;
        activeTasks: SplitTask[];
        poller: {
            errorCount: number;
            isPaused: boolean;
            stuckTaskIds: string[];
            timeoutTaskIds: string[];
        };
        isUploading: boolean;
        isSplitting: boolean;
        isEmbedding: boolean;
        isDownloading: boolean;
    };
    actions: {
        setActiveTab: (tab: 'upload' | 'download') => void;
        handleFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
        setDownloadUrl: (url: string) => void;
        setNovelName: (name: string) => void;
        handleUpload: () => void;
        handleSplit: () => void;
        handleEmbed: () => void;
        handleDownloadAndIngest: () => void;
        manualRefresh: () => Promise<void>;
        setVersion: (version: string) => void;
        setStrategy: (strategy: string) => void;
        setMaxTokens: (tokens: number) => void;
        setOverlapTokens: (tokens: number) => void;
    };
}

export function UploadPanel({ state, actions }: UploadPanelProps) {
    const { activeTab, selectedFile, novelName, downloadUrl, version, strategy, maxTokens, overlapTokens, currentNovelId, ingestStatus, isError, activeTasks, poller, isUploading, isSplitting, isEmbedding, isDownloading } = state;
    const [previewOpen, setPreviewOpen] = useState(false);

    return (
        <div className="rounded-2xl border-2 border-dashed border-amber-200 bg-gradient-to-br from-amber-50/60 via-white to-violet-50/40 p-6 relative">
            {/* Tabs */}
            <div className="flex gap-2 mb-6 p-1 bg-white/50 rounded-lg w-fit border border-gray-100">
                <button
                    onClick={() => actions.setActiveTab('upload')}
                    className={cn(
                        "px-4 py-1.5 text-sm font-medium rounded-md transition-all",
                        activeTab === 'upload' ? "bg-white shadow-sm text-amber-600" : "text-gray-500 hover:text-gray-700"
                    )}
                >
                    本地上传
                </button>
                <button
                    onClick={() => actions.setActiveTab('download')}
                    className={cn(
                        "px-4 py-1.5 text-sm font-medium rounded-md transition-all",
                        activeTab === 'download' ? "bg-white shadow-sm text-violet-600" : "text-gray-500 hover:text-gray-700"
                    )}
                >
                    远程下载
                </button>
            </div>

            {/* Content Area */}
            {activeTab === 'upload' ? (
                <label
                    htmlFor="file-upload"
                    className="flex flex-col items-center justify-center border-2 border-dashed border-amber-300/70 rounded-xl bg-amber-50/50 hover:bg-amber-50 transition-colors cursor-pointer py-8 px-4 text-center mb-5"
                >
                    <div className="w-14 h-14 rounded-full bg-gradient-to-br from-orange-400 to-amber-500 flex items-center justify-center mb-3">
                        <UploadCloud className="w-6 h-6 text-white" />
                    </div>
                    <p className="text-sm font-medium text-gray-700">拖拽文件到此，或点击选择</p>
                    {selectedFile ? (
                        <span className="mt-2 text-xs bg-amber-100 text-amber-800 px-3 py-1 rounded-full font-medium truncate max-w-full">
                            {selectedFile.name}
                        </span>
                    ) : (
                        <p className="mt-1 text-xs text-gray-400">支持 .txt 格式，最大 50MB</p>
                    )}
                    <input
                        id="file-upload"
                        type="file"
                        accept=".txt"
                        className="hidden"
                        onChange={actions.handleFileChange}
                    />
                </label>
            ) : (
                <div className="space-y-4 mb-5">
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">小说下载地址 URL</label>
                        <input
                            type="url"
                            value={downloadUrl}
                            onChange={(e) => actions.setDownloadUrl(e.target.value)}
                            placeholder="https://example.com/novel/123"
                            className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-violet-400"
                        />
                    </div>
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">保存文件名</label>
                        <input
                            type="text"
                            value={novelName}
                            onChange={(e) => actions.setNovelName(e.target.value)}
                            placeholder="my_novel"
                            className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-violet-400"
                        />
                    </div>
                </div>
            )}

            {/* Config */}
            <div className="flex flex-col gap-4 mb-5">
                <div className="grid grid-cols-4 gap-4">
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">小说名称</label>
                        <input
                            type="text" 
                            value={novelName} 
                            onChange={(e) => actions.setNovelName(e.target.value)}
                            placeholder="例如：九阳帝尊"
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        />
                    </div>
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本标识</label>
                        <input
                            type="text" 
                            value={version} 
                            onChange={(e) => actions.setVersion(e.target.value)}
                            placeholder="v1"
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        />
                    </div>
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">切分策略</label>
                        <select
                            value={strategy}
                            onChange={(e) => actions.setStrategy(e.target.value)}
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        >
                            <option value="semantic">语义场景切分（推荐）</option>
                            <option value="fixed">固定长度切分</option>
                            <option value="chapter">章节切分</option>
                        </select>
                    </div>
                </div>
                <div className="grid grid-cols-4 gap-4">
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">最大 Token / 块</label>
                        <input
                            type="number" 
                            value={maxTokens} 
                            onChange={(e) => actions.setMaxTokens(Number(e.target.value))}
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        />
                    </div>
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">重叠 Token</label>
                        <input
                            type="number" 
                            value={overlapTokens} 
                            onChange={(e) => actions.setOverlapTokens(Number(e.target.value))}
                            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                        />
                    </div>
                </div>
            </div>

            {/* Actions */}
            <div className="flex gap-3 justify-center items-center flex-wrap">
                {activeTab === 'upload' ? (
                    <>
                        <button
                            onClick={actions.handleUpload}
                            disabled={!selectedFile || isUploading}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
                            1. 上传文件
                        </button>

                        <button
                            onClick={actions.handleSplit}
                            disabled={!currentNovelId || isSplitting}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isSplitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                            2. 开始切分
                        </button>

                        <button
                            onClick={() => setPreviewOpen(true)}
                            disabled={!currentNovelId}
                            className="inline-flex items-center gap-2 h-9 px-4 py-2 rounded-full text-sm font-medium text-indigo-700 bg-indigo-50 border border-indigo-100 hover:bg-indigo-100 transition-colors disabled:opacity-40 disabled:pointer-events-none"
                        >
                            <Eye className="w-4 h-4" /> 预览效果
                        </button>

                        <button
                            onClick={actions.handleEmbed}
                            disabled={!currentNovelId || isEmbedding}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-600 hover:to-teal-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isEmbedding ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                            3. 确认向量化
                        </button>
                    </>
                ) : (
                    <button
                        onClick={actions.handleDownloadAndIngest}
                        disabled={!downloadUrl || !novelName || isDownloading}
                        className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-blue-500 hover:from-violet-600 hover:to-blue-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                    >
                        {isDownloading ? <Loader2 className="w-4 h-4 animate-spin" /> : <DownloadCloud className="w-4 h-4" />}
                        下载并发送到任务队列
                    </button>
                )}
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

            {/* Polling Status */}
            <TaskPollerStatus tasks={activeTasks} poller={poller} onManualRefresh={actions.manualRefresh} />

            {/* Split Preview Modal */}
            <SplitPreviewModal isOpen={previewOpen} onClose={() => setPreviewOpen(false)} novelId={currentNovelId} />
        </div>
    );
}