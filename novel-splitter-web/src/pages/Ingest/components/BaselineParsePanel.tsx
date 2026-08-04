import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AlertCircle, ArrowRight, FileSearch, ListTree, Loader2 } from 'lucide-react';
import { novelApi, type NovelChapterDto } from '@/api/novelApi';

const PREVIEW_COUNT = 5;

interface BaselineParsePanelProps {
  novelId?: string;
  /** 上传后的章节解析任务是否仍在进行中；解析完成前不应请求章节列表（后端 PENDING/SPLITTING 会拒绝）。 */
  isPolling?: boolean;
}

/**
 * 章节解析结果面板：仅展示已自动解析的章节目录，切分策略在 /process 配置。
 */
export function BaselineParsePanel({ novelId, isPolling = false }: BaselineParsePanelProps) {
    const { data: chapters = [], isLoading: chaptersLoading, isError: chaptersError } = useQuery<NovelChapterDto[]>({
        queryKey: ['chapters', novelId],
        queryFn: () => novelApi.getChapters(novelId!),
        enabled: !!novelId && !isPolling,
    });

    const previewChapters = chapters.slice(0, PREVIEW_COUNT);

    return (
        <div className="rounded-2xl border-2 border-dashed border-indigo-200 bg-gradient-to-br from-indigo-50/60 via-white to-violet-50/40 p-6 relative">
            {/* Header */}
            <div className="flex items-start gap-3 mb-5">
                <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shrink-0">
                    <FileSearch className="w-5 h-5 text-white" />
                </div>
                <div>
                    <h2 className="text-base font-semibold text-gray-800 flex items-center gap-2">
                        章节解析结果
                    </h2>
                    <p className="text-xs text-gray-500 mt-1 leading-relaxed">
                        上传即自动解析；此处仅展示章节目录。切分策略请在 /process 配置。
                    </p>
                </div>
            </div>

            {!novelId ? (
                <div className="flex items-center gap-2 px-4 py-3 rounded-xl bg-gray-50 border border-gray-200 text-sm text-gray-500">
                    <AlertCircle className="w-4 h-4 flex-shrink-0" />
                    请先上传小说。
                </div>
            ) : isPolling ? (
                <div className="flex items-center gap-2 px-4 py-3 rounded-xl bg-blue-50 border border-blue-200 text-sm text-blue-700">
                    <Loader2 className="w-4 h-4 animate-spin flex-shrink-0" />
                    章节解析中，完成后自动展示章节目录…
                </div>
            ) : (
                <div className="space-y-4">
                    {/* 章节结果 */}
                    <div className="rounded-xl border border-gray-200 bg-white/80 overflow-hidden">
                        <div className="px-3 py-2 border-b border-gray-100 bg-white/80 flex items-center justify-between">
                            <span className="text-xs font-semibold text-gray-600 flex items-center gap-1.5">
                                <ListTree className="w-3.5 h-3.5" />
                                解析结果
                            </span>
                            {chapters.length > 0 && (
                                <span className="text-xs bg-indigo-100 text-indigo-700 font-medium px-2.5 py-0.5 rounded-full">
                                    已解析 {chapters.length} 章
                                </span>
                            )}
                        </div>
                        <div className="max-h-64 overflow-y-auto divide-y divide-gray-100">
                            {chaptersLoading ? (
                                <div className="flex justify-center py-10">
                                    <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
                                </div>
                            ) : chaptersError ? (
                                <p className="px-3 py-8 text-center text-sm text-red-400">
                                    章节列表加载失败，请确认章节解析已完成。
                                </p>
                            ) : chapters.length === 0 ? (
                                <p className="px-3 py-8 text-center text-sm text-gray-400">
                                    暂无章节。
                                </p>
                            ) : (
                                <>
                                    {previewChapters.map((ch) => (
                                        <div key={ch.id} className="px-3 py-2.5 text-sm flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 bg-white">
                                            <span className="text-gray-900 font-medium truncate">
                                                <span className="text-gray-400 font-mono text-xs mr-2">{ch.index}</span>
                                                {ch.title}
                                            </span>
                                            <span className="text-xs text-gray-500 shrink-0 font-mono">
                                                行 {ch.startParagraphIndex}–{ch.endParagraphIndex}
                                            </span>
                                        </div>
                                    ))}
                                    {chapters.length > PREVIEW_COUNT && (
                                        <p className="px-3 py-2 text-xs text-gray-400 text-center bg-white">
                                            仅展示前 {PREVIEW_COUNT} 章，共 {chapters.length} 章。
                                        </p>
                                    )}
                                </>
                            )}
                        </div>
                    </div>

                    {/* 前往 /process */}
                    {chapters.length > 0 && (
                        <div className="flex justify-center">
                            <Link
                                to={`/process?novelId=${encodeURIComponent(novelId)}`}
                                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-full text-sm font-medium text-white bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600 hover:shadow-lg transition-all"
                            >
                                <ArrowRight className="w-4 h-4" />
                                前往 /process 做版本实验
                            </Link>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
