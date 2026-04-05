import { UploadCloud, FileText, Loader2, CheckCircle, AlertCircle } from "lucide-react";
import { cn } from "@/lib/utils";

interface UploadPanelProps {
    state: {
        selectedFile: File | null;
        version: string;
        maxScenes: number;
        ingestStatus: string;
        isError: boolean;
        isUploading: boolean;
        isIngesting: boolean;
    };
    actions: {
        handleFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
        handleUpload: () => void;
        handleIngest: () => void;
        setVersion: (version: string) => void;
        setMaxScenes: (scenes: number) => void;
    };
}

export function UploadPanel({ state, actions }: UploadPanelProps) {
    const { selectedFile, version, maxScenes, ingestStatus, isError, isUploading, isIngesting } = state;

    return (
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
                <input 
                    id="file-upload" 
                    type="file" 
                    accept=".txt" 
                    className="hidden" 
                    onChange={actions.handleFileChange} 
                />
            </label>

            {/* Config */}
            <div className="grid grid-cols-2 gap-4 mb-5">
                <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本号 Version</label>
                    <input
                        type="text" 
                        value={version} 
                        onChange={(e) => actions.setVersion(e.target.value)}
                        placeholder="v1"
                        className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                    />
                </div>
                <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">最大场景数（0 = 全部）</label>
                    <input
                        type="number" 
                        value={maxScenes} 
                        onChange={(e) => actions.setMaxScenes(Number(e.target.value))}
                        className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                    />
                </div>
            </div>

            {/* Actions */}
            <div className="flex gap-3 justify-center">
                <button
                    onClick={actions.handleUpload}
                    disabled={!selectedFile || isUploading}
                    className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
                >
                    {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
                    上传文件
                </button>

                <button
                    onClick={actions.handleIngest}
                    disabled={!selectedFile || isIngesting}
                    className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-blue-500 hover:from-violet-600 hover:to-blue-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                >
                    {isIngesting ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
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
    );
}