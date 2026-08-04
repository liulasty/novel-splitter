import { UploadCloud, Loader2, CheckCircle, AlertCircle, ArrowRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { Link } from "react-router-dom";
import { useQuery } from '@tanstack/react-query';
import { novelApi, type ChapterStrategy } from "@/api/novelApi";

interface UploadPanelProps {
    state: {
        selectedFile: File | null;
        currentNovelId: string;
        ingestStatus: string;
        isError: boolean;
        isUploading: boolean;
        strategy: string;
        chapterTitleRegex: string;
        isPolling: boolean;
        polledTask?: { status?: string; progress?: number | null; message?: string } | null;
    };
    actions: {
        handleFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
        handleUpload: () => void;
        clearSelectedNovel: () => void;
        setStrategy: (v: string) => void;
        setChapterTitleRegex: (v: string) => void;
    };
}

export function UploadPanel({ state, actions }: UploadPanelProps) {
    const {
        selectedFile, currentNovelId, ingestStatus, isError, isUploading,
        strategy, chapterTitleRegex, isPolling, polledTask,
    } = state;

    const { data: strategies = [] } = useQuery<ChapterStrategy[]>({
        queryKey: ['chapter-strategies'],
        queryFn: novelApi.listChapterStrategies,
        staleTime: Infinity,
    });

    return (
        <div className="rounded-2xl border-2 border-dashed border-amber-200 bg-gradient-to-br from-amber-50/60 via-white to-violet-50/40 p-6 relative">

            {/* File drop / select */}
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

            {/* 章节识别策略 */}
            <div className="space-y-1.5 mb-5">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">章节识别策略</p>
                <select
                    value={strategy}
                    onChange={(e) => actions.setStrategy(e.target.value)}
                    className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                >
                    {strategies.map((s) => (
                        <option key={s.key} value={s.key}>{s.label}</option>
                    ))}
                </select>
                {strategies.length > 0 && (
                    <p className="text-xs text-gray-400">{strategies.find((s) => s.key === strategy)?.description}</p>
                )}
                {strategy === 'CUSTOM' && (
                    <input
                        type="text"
                        value={chapterTitleRegex}
                        onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
                        placeholder="例如：^第\\d+章.*（整行匹配）"
                        className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                    />
                )}
            </div>

            {/* Actions */}
            <div className="flex gap-3 justify-center items-center flex-wrap">
                <button
                    type="button"
                    onClick={actions.handleUpload}
                    disabled={!selectedFile || isUploading}
                    className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
                >
                    {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
                    上传文件
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

            {isPolling && (
                <div className="mt-4 flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-blue-50 text-blue-700">
                    <Loader2 className="w-4 h-4 animate-spin flex-shrink-0" />
                    章节解析中…{polledTask?.progress != null ? ` ${polledTask.progress}%` : ''}
                    {polledTask?.message ? `（${polledTask.message}）` : ''}
                </div>
            )}

            {/* Post-upload: link to process page */}
            {currentNovelId && !isError && (
                <div className="mt-4 flex justify-center">
                    <Link
                        to={`/process?novelId=${encodeURIComponent(currentNovelId)}`}
                        className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full text-sm font-medium text-white bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600 hover:shadow-lg transition-all"
                    >
                        <ArrowRight className="w-4 h-4" />
                        前往场景处理
                    </Link>
                </div>
            )}
        </div>
    );
}
