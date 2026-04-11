import { UploadCloud, FileText, Loader2, CheckCircle, AlertCircle, DownloadCloud, Eye, ListChecks, XCircle, Library, RefreshCw, ClipboardCheck } from "lucide-react";
import { cn } from "@/lib/utils";
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { SplitPreviewModal } from './SplitPreviewModal';
import { ChapterReviewModal } from './ChapterReviewModal';
import { TaskPollerStatus } from './TaskPollerStatus';
import type { SplitTask } from "@/api/taskApi";

interface UploadPanelProps {
    state: {
        activeTab: 'upload' | 'download';
        selectedFile: File | null;
        novelName: string;
        downloadUrl: string;
        version: string;
        maxTokens: number;
        overlapTokens: number;
        currentNovelId: string;
        chapterReviewAck: boolean;
        chapterTitleRegex: string;
        ingestStatus: string;
        isError: boolean;
        tasks: SplitTask[];
        activeTasks: SplitTask[];
        poller: {
            errorCount: number;
            isPaused: boolean;
            stuckTaskIds: string[];
            timeoutTaskIds: string[];
        };
        isUploading: boolean;
        isChapterParsing: boolean;
        isSceneSplitting: boolean;
        isEmbedding: boolean;
        isDownloading: boolean;
    };
    actions: {
        setActiveTab: (tab: 'upload' | 'download') => void;
        handleFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
        setDownloadUrl: (url: string) => void;
        setNovelName: (name: string) => void;
        handleUpload: () => void;
        handleChapterParse: () => void;
        handleSceneSplit: (triggerEmbed: boolean) => void;
        handleForceReparseChapters: () => void;
        handleEmbed: () => void;
        handleDownloadAndIngest: () => void;
        manualRefresh: () => Promise<void>;
        setVersion: (version: string) => void;
        setMaxTokens: (tokens: number) => void;
        setOverlapTokens: (tokens: number) => void;
        setChapterTitleRegex: (v: string) => void;
        acknowledgeChapterReview: () => void;
        selectNovelById: (novelId: string) => void;
        clearSelectedNovel: () => void;
        addActiveTask: (taskId: string) => void;
    };
}

type NovelPickerTab = 'library' | 'upload';

export function UploadPanel({ state, actions }: UploadPanelProps) {
    const {
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
        isUploading,
        isChapterParsing,
        isSceneSplitting,
        isEmbedding,
        isDownloading,
    } = state;
    const [previewOpen, setPreviewOpen] = useState(false);
    const [chapterReviewOpen, setChapterReviewOpen] = useState(false);
    const [novelPickerTab, setNovelPickerTab] = useState<NovelPickerTab>('library');

    const { data: novelOptions = [] } = useQuery({
        queryKey: ['novelSummaries', 'all'],
        queryFn: () => novelApi.getNovelSummaries('all'),
    });

    const currentMeta = novelOptions.find((n) => n.novelId === currentNovelId);
    const chapterParseBusy = tasks.some(
        (t) =>
            t.novelId === currentNovelId &&
            (t.taskType === 'CHAPTER_PARSE' || t.taskType === 'LOAD') &&
            (t.status === 'PENDING' || t.status === 'PROCESSING')
    );
    const chapterParseSucceeded = tasks.some(
        (t) =>
            t.novelId === currentNovelId &&
            (t.taskType === 'CHAPTER_PARSE' || t.taskType === 'LOAD') &&
            t.status === 'SUCCESS'
    );
    const structurallyReady =
        ['PARSED', 'SPLIT_COMPLETED', 'COMPLETED'].includes(currentMeta?.status ?? '') || chapterParseSucceeded;
    const canSceneSplit =
        !!currentNovelId && structurallyReady && chapterReviewAck && !chapterParseBusy;

    return (
        <div className="rounded-2xl border-2 border-dashed border-amber-200 bg-gradient-to-br from-amber-50/60 via-white to-violet-50/40 p-6 relative">
            {/* 当前小说：书库（已上传） vs 需要上传 */}
            <div className="mb-5 rounded-xl border border-slate-200 bg-white/80 px-4 py-3 space-y-3">
                <div className="flex items-center justify-between gap-2 flex-wrap">
                    <span className="text-xs font-semibold text-slate-500 uppercase tracking-wide flex items-center gap-1.5">
                        <ListChecks className="w-3.5 h-3.5" />
                        当前操作的小说
                    </span>
                    {currentNovelId ? (
                        <button
                            type="button"
                            onClick={() => actions.clearSelectedNovel()}
                            className="text-xs text-slate-500 hover:text-red-600 inline-flex items-center gap-1 shrink-0"
                        >
                            <XCircle className="w-3.5 h-3.5" />
                            清除选择
                        </button>
                    ) : null}
                </div>

                <div className="flex flex-wrap gap-2 p-1 rounded-lg bg-slate-100/80 border border-slate-200/80 w-full sm:w-fit">
                    <button
                        type="button"
                        onClick={() => setNovelPickerTab('library')}
                        className={cn(
                            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-md transition-all',
                            novelPickerTab === 'library'
                                ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200'
                                : 'text-slate-600 hover:text-slate-900'
                        )}
                    >
                        <Library className="w-3.5 h-3.5" />
                        书库列表
                    </button>
                    <button
                        type="button"
                        onClick={() => setNovelPickerTab('upload')}
                        className={cn(
                            'inline-flex items-center gap-1.5 px-3 py-1.5 text-sm font-medium rounded-md transition-all',
                            novelPickerTab === 'upload'
                                ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200'
                                : 'text-slate-600 hover:text-slate-900'
                        )}
                    >
                        <UploadCloud className="w-3.5 h-3.5" />
                        需要上传小说
                    </button>
                </div>

                {novelPickerTab === 'library' ? (
                    <div className="space-y-2">
                        <p className="text-[11px] text-slate-500">
                            点选已入库小说；先<strong>解析章节</strong>（Load），再<strong>场景切分</strong>（Split）。同一本书可用不同场景版本号生成多套切片。知识库或{' '}
                            <span className="font-mono text-slate-600">?novelId=</span> 可同步当前书。
                        </p>
                        <div className="max-h-48 overflow-y-auto rounded-lg border border-slate-200 bg-white divide-y divide-slate-100">
                            {currentNovelId && !novelOptions.some((n) => n.novelId === currentNovelId) ? (
                                <button
                                    type="button"
                                    onClick={() => actions.selectNovelById(currentNovelId)}
                                    className={cn(
                                        'w-full text-left px-3 py-2.5 text-sm transition-colors',
                                        'bg-amber-50/80 text-amber-900 hover:bg-amber-50'
                                    )}
                                >
                                    <span className="font-medium">当前会话（未在书库列表中）</span>
                                    <span className="block text-xs font-mono text-amber-800/80 mt-0.5 break-all">{currentNovelId}</span>
                                </button>
                            ) : null}
                            {novelOptions.length === 0 && !(currentNovelId && !novelOptions.some((n) => n.novelId === currentNovelId)) ? (
                                <div className="px-3 py-6 text-center text-sm text-slate-400">暂无已登记小说，请切换到「需要上传小说」添加文件。</div>
                            ) : null}
                            {novelOptions.map((n) => {
                                const selected = n.novelId === currentNovelId;
                                return (
                                    <button
                                        key={n.novelId}
                                        type="button"
                                        onClick={() => actions.selectNovelById(n.novelId)}
                                        className={cn(
                                            'w-full text-left px-3 py-2.5 text-sm transition-colors',
                                            selected
                                                ? 'bg-blue-50 text-blue-900 ring-inset ring-1 ring-blue-100'
                                                : 'text-slate-800 hover:bg-slate-50'
                                        )}
                                    >
                                        <span className="font-medium line-clamp-1">{n.title || n.novelId}</span>
                                        <span className="block text-xs text-slate-500 mt-0.5">
                                            {n.status ?? '?'} · <span className="font-mono">{n.novelId}</span>
                                        </span>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                ) : (
                    <p className="text-[11px] text-slate-500">
                        在下方选择本地上传或远程下载；默认仅触发<strong>章节解析</strong>（若走下载流水线）。上传后再到书库：先解析章节，再场景切分。
                    </p>
                )}

                {currentNovelId ? (
                    <p className="text-[11px] text-slate-500 font-mono break-all border-t border-slate-100 pt-2">
                        当前 novelId: {currentNovelId}
                    </p>
                ) : null}
            </div>

            {/* 上传来源：仅「需要上传小说」 */}
            {novelPickerTab === 'upload' ? (
            <>
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
            </>
            ) : null}

            {/* Config */}
            <div className="flex flex-col gap-4 mb-5">
                {novelPickerTab === 'upload' ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                        <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
                                章节标题正则（可选）
                            </label>
                            <input
                                type="text"
                                value={chapterTitleRegex}
                                onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
                                placeholder="整行匹配 Java 正则，留空用默认"
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                        </div>
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
                        <div className="space-y-1.5 sm:col-span-2 lg:col-span-1">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">说明</label>
                            <p className="text-[11px] text-gray-500 leading-relaxed pt-1">
                                上传后请先「解析章节」，打开「章节校对」确认目录后再「场景切分」。下方块大小仅作用于场景阶段。
                            </p>
                        </div>
                    </div>
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="space-y-1.5 sm:col-span-3">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
                                章节标题正则（可选，整行匹配）
                            </label>
                            <input
                                type="text"
                                value={chapterTitleRegex}
                                onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
                                placeholder="留空则用默认规则；示例 ^第\\d+章.*"
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                        </div>
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">场景版本标识</label>
                            <input
                                type="text"
                                value={version}
                                onChange={(e) => actions.setVersion(e.target.value)}
                                placeholder="v2-c512-o96（不同块规则请用不同 version）"
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                            <p className="text-[10px] text-slate-500 leading-snug">
                                同名 version 会覆盖该小说下已有场景切片；换块大小/重叠并存多套时务必改版本号。
                            </p>
                        </div>
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">场景块大小（字）</label>
                            <input
                                type="number"
                                value={maxTokens}
                                onChange={(e) => actions.setMaxTokens(Number(e.target.value))}
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                        </div>
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">重叠（字）</label>
                            <input
                                type="number"
                                value={overlapTokens}
                                onChange={(e) => actions.setOverlapTokens(Number(e.target.value))}
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                        </div>
                    </div>
                )}

                {novelPickerTab === 'library' ? (
                    <div className="space-y-2 rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-3 text-[11px] text-slate-600 leading-relaxed">
                        <p>
                            <span className="font-semibold text-slate-700">① 解析章节</span>（CHAPTER_PARSE / Load 队列）：原文 → 章节目录与解析文件，<span className="text-slate-800">不会</span>自动场景切分。
                        </p>
                        <p>
                            <span className="font-semibold text-slate-700">② 场景切分</span>（SCENE_SPLIT / Split 队列）：需小说已处于已解析状态；完整流水线可在后续通过编排或 <code className="text-[10px] bg-white px-1 rounded">pipeline + splitEntry=SCENE_ONLY</code> 扩展。
                        </p>
                        {currentNovelId ? (
                            <p className="text-slate-500 border-t border-slate-200/80 pt-2 mt-1">
                                当前书状态：<span className="font-mono text-slate-700">{currentMeta?.status ?? '（列表外会话）'}</span>
                                {chapterParseBusy ? ' · 章节任务进行中…' : null}
                                {structurallyReady && !chapterReviewAck && !chapterParseBusy ? (
                                    <span> · 请打开「章节校对」并确认无误后再场景切分</span>
                                ) : null}
                                {!structurallyReady && !chapterParseBusy ? ' · 完成章节解析后可校对并场景切分' : null}
                            </p>
                        ) : null}
                    </div>
                ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">场景块大小（字）</label>
                            <input
                                type="number"
                                value={maxTokens}
                                onChange={(e) => actions.setMaxTokens(Number(e.target.value))}
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                        </div>
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">重叠（字）</label>
                            <input
                                type="number"
                                value={overlapTokens}
                                onChange={(e) => actions.setOverlapTokens(Number(e.target.value))}
                                className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-amber-400"
                            />
                        </div>
                    </div>
                )}
            </div>

            {/* Actions */}
            <div className="flex gap-3 justify-center items-center flex-wrap">
                {novelPickerTab === 'library' ? (
                    <>
                        <button
                            type="button"
                            onClick={actions.handleChapterParse}
                            disabled={!currentNovelId || isChapterParsing || chapterParseBusy}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isChapterParsing || chapterParseBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                            ① 解析章节
                        </button>
                        <button
                            type="button"
                            onClick={actions.handleForceReparseChapters}
                            disabled={!currentNovelId || chapterParseBusy}
                            title="独立 Load API，force=true，可选用上方章节正则"
                            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full text-xs font-medium border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            <RefreshCw className="w-3.5 h-3.5" />
                            强制重解析
                        </button>
                        <button
                            type="button"
                            onClick={() => setChapterReviewOpen(true)}
                            disabled={!currentNovelId}
                            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full text-xs font-medium border border-indigo-200 bg-indigo-50 text-indigo-800 hover:bg-indigo-100 transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            <ClipboardCheck className="w-3.5 h-3.5" />
                            章节校对
                        </button>
                        <button
                            type="button"
                            onClick={() => actions.handleSceneSplit(false)}
                            disabled={!currentNovelId || !canSceneSplit || isSceneSplitting}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-indigo-600 hover:from-violet-600 hover:to-indigo-700 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isSceneSplitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                            ② 场景切分
                        </button>
                        <button
                            type="button"
                            onClick={() => actions.handleSceneSplit(true)}
                            disabled={!currentNovelId || !canSceneSplit || isSceneSplitting}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-fuchsia-500 to-purple-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            ② 切分并入库
                        </button>
                        <button
                            type="button"
                            onClick={() => setPreviewOpen(true)}
                            disabled={!currentNovelId}
                            className="inline-flex items-center gap-2 h-9 px-4 py-2 rounded-full text-sm font-medium text-indigo-700 bg-indigo-50 border border-indigo-100 hover:bg-indigo-100 transition-colors disabled:opacity-40 disabled:pointer-events-none"
                        >
                            <Eye className="w-4 h-4" /> 预览效果
                        </button>
                        <button
                            type="button"
                            onClick={actions.handleEmbed}
                            disabled={!currentNovelId || isEmbedding}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-600 hover:to-teal-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isEmbedding ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                            ③ 仅向量化
                        </button>
                    </>
                ) : activeTab === 'upload' ? (
                    <>
                        <button
                            type="button"
                            onClick={actions.handleUpload}
                            disabled={!selectedFile || isUploading}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium border border-gray-200 bg-white text-gray-700 hover:bg-gray-50 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isUploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
                            1. 上传文件
                        </button>
                        <button
                            type="button"
                            onClick={actions.handleChapterParse}
                            disabled={!currentNovelId || isChapterParsing || chapterParseBusy}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isChapterParsing || chapterParseBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                            2. 解析章节
                        </button>
                        <button
                            type="button"
                            onClick={() => setChapterReviewOpen(true)}
                            disabled={!currentNovelId}
                            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-full text-xs font-medium border border-indigo-200 bg-indigo-50 text-indigo-800 hover:bg-indigo-100 transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            <ClipboardCheck className="w-3.5 h-3.5" />
                            章节校对
                        </button>
                        <button
                            type="button"
                            onClick={() => actions.handleSceneSplit(false)}
                            disabled={!currentNovelId || !canSceneSplit || isSceneSplitting}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-indigo-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isSceneSplitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                            3. 场景切分
                        </button>
                        <button
                            type="button"
                            onClick={() => setPreviewOpen(true)}
                            disabled={!currentNovelId}
                            className="inline-flex items-center gap-2 h-9 px-4 py-2 rounded-full text-sm font-medium text-indigo-700 bg-indigo-50 border border-indigo-100 hover:bg-indigo-100 transition-colors disabled:opacity-40 disabled:pointer-events-none"
                        >
                            <Eye className="w-4 h-4" /> 预览
                        </button>
                        <button
                            type="button"
                            onClick={actions.handleEmbed}
                            disabled={!currentNovelId || isEmbedding}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-600 hover:to-teal-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {isEmbedding ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCircle className="w-4 h-4" />}
                            向量化
                        </button>
                    </>
                ) : (
                    <button
                        type="button"
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
            <ChapterReviewModal
                open={chapterReviewOpen}
                novelId={currentNovelId}
                version={version}
                onClose={() => setChapterReviewOpen(false)}
                onAcknowledge={actions.acknowledgeChapterReview}
                onReparseTaskCreated={(taskId) => actions.addActiveTask(taskId)}
            />
        </div>
    );
}