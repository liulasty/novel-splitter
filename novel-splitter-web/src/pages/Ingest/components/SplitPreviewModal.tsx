import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { Loader2, FileText, X, ChevronRight, BookOpen } from 'lucide-react';
import { cn } from '@/lib/utils';

interface SplitPreviewModalProps {
    isOpen: boolean;
    onClose: () => void;
    novelId: string;
}

export function SplitPreviewModal({ isOpen, onClose, novelId }: SplitPreviewModalProps) {
    const [selectedChapterId, setSelectedChapterId] = useState<string | null>(null);

    const { data: chapters, isLoading: isChaptersLoading } = useQuery({
        queryKey: ['chapters', novelId],
        queryFn: () => novelApi.getChapters(novelId),
        enabled: isOpen && !!novelId,
    });

    const { data: scenes, isLoading: isScenesLoading } = useQuery({
        queryKey: ['scenes', novelId, selectedChapterId],
        queryFn: () => novelApi.getScenes(novelId, selectedChapterId!),
        enabled: isOpen && !!novelId && !!selectedChapterId,
    });

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4 sm:p-6">
            <div className="bg-white rounded-2xl w-full max-w-6xl h-[80vh] shadow-2xl flex flex-col overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50/50">
                    <h2 className="text-xl font-semibold text-gray-900 flex items-center gap-2">
                        <FileText className="w-5 h-5 text-indigo-600" />
                        切分结果预览
                        {novelId && <span className="text-sm font-normal text-gray-500 ml-2">ID: {novelId}</span>}
                    </h2>
                    <button
                        onClick={onClose}
                        className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full transition-colors"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Body */}
                <div className="flex flex-1 overflow-hidden">
                    {/* Left: Chapters Tree */}
                    <div className="w-1/3 flex flex-col border-r border-gray-100 bg-white">
                        <div className="p-4 border-b border-gray-100 bg-gray-50/30">
                            <h3 className="text-sm font-semibold text-gray-700 flex items-center gap-2">
                                <BookOpen className="w-4 h-4" /> 章节列表
                            </h3>
                        </div>
                        <div className="flex-1 overflow-y-auto p-3 space-y-1">
                            {isChaptersLoading ? (
                                <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-gray-400" /></div>
                            ) : chapters?.length === 0 ? (
                                <div className="text-center py-8 text-sm text-gray-500">暂无章节数据，请确认是否已完成切分任务。</div>
                            ) : (
                                chapters?.map((chapter) => (
                                    <button
                                        key={chapter.chapterId}
                                        onClick={() => setSelectedChapterId(chapter.chapterId)}
                                        className={cn(
                                            "w-full text-left px-3 py-2.5 rounded-lg text-sm font-medium transition-colors flex items-center justify-between group",
                                            selectedChapterId === chapter.chapterId 
                                                ? "bg-indigo-50 text-indigo-700" 
                                                : "text-gray-600 hover:bg-gray-50"
                                        )}
                                    >
                                        <span className="truncate flex-1 pr-2">{chapter.title}</span>
                                        <ChevronRight className={cn(
                                            "w-4 h-4 opacity-0 group-hover:opacity-100 transition-opacity",
                                            selectedChapterId === chapter.chapterId ? "opacity-100 text-indigo-500" : "text-gray-400"
                                        )} />
                                    </button>
                                ))
                            )}
                        </div>
                    </div>

                    {/* Right: Scenes List */}
                    <div className="w-2/3 bg-slate-50 flex flex-col relative">
                        <div className="p-4 border-b border-gray-100 bg-white flex justify-between items-center">
                            <h3 className="text-sm font-semibold text-gray-700">切分片段 (Scenes)</h3>
                            {scenes && <span className="text-xs text-gray-500">共 {scenes.length} 个片段</span>}
                        </div>
                        <div className="flex-1 overflow-y-auto p-6 space-y-4">
                            {!selectedChapterId ? (
                                <div className="h-full flex flex-col items-center justify-center text-gray-400">
                                    <BookOpen className="w-12 h-12 mb-3 text-gray-300" />
                                    <p className="text-sm">请在左侧选择一个章节</p>
                                </div>
                            ) : isScenesLoading ? (
                                <div className="h-full flex flex-col items-center justify-center">
                                    <Loader2 className="w-8 h-8 animate-spin text-indigo-500 mb-4" />
                                </div>
                            ) : scenes?.length === 0 ? (
                                <div className="text-center py-8 text-sm text-gray-500">该章节暂无切分片段。</div>
                            ) : (
                                scenes?.map((scene, idx) => (
                                    <div key={scene.sceneId} className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                                        <div className="flex items-center justify-between px-4 py-2 bg-gray-50/80 border-b border-gray-100">
                                            <span className="text-xs font-semibold text-gray-500">Scene #{idx + 1}</span>
                                            <div className="flex items-center gap-2">
                                                <span className="px-2 py-0.5 rounded text-[10px] font-medium tracking-wide bg-blue-100 text-blue-700">
                                                    Tokens: {scene.tokens}
                                                </span>
                                            </div>
                                        </div>
                                        <div className="p-4 text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                                            {scene.content}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
