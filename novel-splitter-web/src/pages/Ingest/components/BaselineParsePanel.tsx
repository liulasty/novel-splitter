import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AlertCircle, ArrowRight, Braces, CheckCircle, FileSearch, ListTree, Loader2 } from 'lucide-react';
import { getApiErrorMessage } from '@/lib/apiError';
import { novelApi, type NovelChapterDto } from '@/api/novelApi';
import { taskApi, type SplitTask } from '@/api/taskApi';

const DEFAULT_STRATEGY = 'CN_CHAPTER';
const PREVIEW_COUNT = 5;

interface BaselineParsePanelProps {
  novelId?: string;
}

/**
 * 基准解析面板（阶段一）：一次性识别章节标题，生成基准章节目录。
 * 提交后轮询任务至 SUCCESS/FAILED；SUCCESS 展示章节目录，FAILED 提示已整体回滚。
 */
export function BaselineParsePanel({ novelId }: BaselineParsePanelProps) {
    const queryClient = useQueryClient();

    const [strategy, setStrategy] = useState(DEFAULT_STRATEGY);
    const [chapterTitleRegex, setChapterTitleRegex] = useState('');
    const [pollingTaskId, setPollingTaskId] = useState('');
    const [lastParseResult, setLastParseResult] = useState<'success' | 'failed' | null>(null);
    const terminalHandledRef = useRef(false);

    const { data: strategies = [] } = useQuery({
        queryKey: ['chapter-strategies'],
        queryFn: novelApi.listChapterStrategies,
        staleTime: Infinity,
    });

    // 后端策略表变化导致当前选择失效时，回退到第一个可用策略。
    useEffect(() => {
        if (strategies.length > 0 && !strategies.some((s) => s.key === strategy)) {
            setStrategy(strategies[0].key);
        }
    }, [strategies, strategy]);

    // 切换小说时重置解析状态。
    useEffect(() => {
        setPollingTaskId('');
        setLastParseResult(null);
        terminalHandledRef.current = false;
    }, [novelId]);

    const isCustomStrategy = strategy === 'CUSTOM';

    const baselineMutation = useMutation({
        mutationFn: (id: string) =>
            novelApi.baselineParse(id, {
                strategy,
                ...(isCustomStrategy && chapterTitleRegex.trim() !== ''
                    ? { chapterTitleRegex: chapterTitleRegex.trim() }
                    : {}),
            }),
        onSuccess: (data) => {
            terminalHandledRef.current = false;
            setLastParseResult(null);
            toast.success(`基准解析已提交：${data.message}`);
            if (data.taskId) {
                setPollingTaskId(data.taskId);
            }
        },
        onError: (error: unknown) => {
            toast.error(getApiErrorMessage(error, '基准解析提交失败'));
        },
    });

    // 轮询基准解析任务直至 SUCCESS / FAILED。
    const { data: polledTask } = useQuery<SplitTask>({
        queryKey: ['baselineTask', pollingTaskId],
        queryFn: () => taskApi.getTask(pollingTaskId!),
        enabled: !!pollingTaskId,
        refetchInterval: 2000,
    });

    useEffect(() => {
        const status = polledTask?.status;
        if (!status || (status !== 'SUCCESS' && status !== 'FAILED')) return;
        if (terminalHandledRef.current) return;
        terminalHandledRef.current = true;

        if (status === 'SUCCESS') {
            setLastParseResult('success');
            toast.success('基准解析完成');
            if (novelId) {
                queryClient.invalidateQueries({ queryKey: ['chapters', novelId] });
            }
        } else {
            setLastParseResult('failed');
            toast.error('基准解析失败，已整体回滚无残留');
        }
        setPollingTaskId('');
    }, [polledTask?.status, novelId, queryClient]);

    const { data: chapters = [], isLoading: chaptersLoading } = useQuery<NovelChapterDto[]>({
        queryKey: ['chapters', novelId],
        queryFn: () => novelApi.getChapters(novelId!),
        enabled: !!novelId,
    });

    const isPolling = !!pollingTaskId;
    const previewChapters = chapters.slice(0, PREVIEW_COUNT);

    const handleParse = () => {
        if (!novelId) {
            toast.error('请先上传小说');
            return;
        }
        baselineMutation.mutate(novelId);
    };

    return (
        <div className="rounded-2xl border-2 border-dashed border-indigo-200 bg-gradient-to-br from-indigo-50/60 via-white to-violet-50/40 p-6 relative">
            {/* Header */}
            <div className="flex items-start gap-3 mb-5">
                <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shrink-0">
                    <FileSearch className="w-5 h-5 text-white" />
                </div>
                <div>
                    <h2 className="text-base font-semibold text-gray-800 flex items-center gap-2">
                        基准解析
                        <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-700">
                            Stage 1
                        </span>
                    </h2>
                    <p className="text-xs text-gray-500 mt-1 leading-relaxed">
                        一次性识别章节标题，生成基准章节目录作为 /process 版本实验的基线。失败自动整体回滚，无残留数据。
                    </p>
                </div>
            </div>

            {!novelId ? (
                <div className="flex items-center gap-2 px-4 py-3 rounded-xl bg-gray-50 border border-gray-200 text-sm text-gray-500">
                    <AlertCircle className="w-4 h-4 flex-shrink-0" />
                    请先上传小说，再进行基准解析。
                </div>
            ) : (
                <div className="space-y-4">
                    {/* 识别策略 */}
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">章节识别策略</label>
                        <select
                            value={strategy}
                            onChange={(e) => setStrategy(e.target.value)}
                            className="w-full h-10 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                        >
                            {strategies.map((s) => (
                                <option key={s.key} value={s.key}>{s.label}</option>
                            ))}
                        </select>
                        {strategies.length > 0 && (
                            <p className="text-xs text-gray-400 mt-1">
                                {strategies.find((s) => s.key === strategy)?.description}
                            </p>
                        )}
                    </div>

                    {/* CUSTOM 正则 */}
                    {isCustomStrategy && (
                        <div className="space-y-1.5">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
                                章节标题正则（整行匹配）
                            </label>
                            <div className="relative">
                                <Braces className="w-4 h-4 text-gray-400 absolute left-3 top-1/2 -translate-y-1/2" />
                                <input
                                    type="text"
                                    value={chapterTitleRegex}
                                    onChange={(e) => setChapterTitleRegex(e.target.value)}
                                    placeholder="例如：^第\\d+章.*"
                                    className="w-full h-10 rounded-lg border border-gray-200 bg-white pl-9 pr-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
                                />
                            </div>
                        </div>
                    )}

                    {/* 操作 */}
                    <div className="flex gap-3 justify-center items-center flex-wrap">
                        <button
                            type="button"
                            onClick={handleParse}
                            disabled={baselineMutation.isPending || isPolling}
                            className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-indigo-500 to-violet-500 hover:from-indigo-600 hover:to-violet-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
                        >
                            {baselineMutation.isPending || isPolling ? (
                                <Loader2 className="w-4 h-4 animate-spin" />
                            ) : (
                                <FileSearch className="w-4 h-4" />
                            )}
                            解析基准
                        </button>
                    </div>

                    {/* 轮询状态 */}
                    {isPolling && (
                        <div className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-blue-50 text-blue-700">
                            <Loader2 className="w-4 h-4 animate-spin flex-shrink-0" />
                            基准解析中…
                            {polledTask?.progress != null ? ` ${polledTask.progress}%` : ''}
                            {polledTask?.message ? `（${polledTask.message}）` : ''}
                        </div>
                    )}
                    {!isPolling && lastParseResult === 'success' && (
                        <div className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-green-50 text-green-700">
                            <CheckCircle className="w-4 h-4 flex-shrink-0" />
                            基准解析完成，章节目录已就绪
                        </div>
                    )}
                    {!isPolling && lastParseResult === 'failed' && (
                        <div className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium bg-red-50 text-red-700">
                            <AlertCircle className="w-4 h-4 flex-shrink-0" />
                            基准解析失败，已整体回滚无残留
                        </div>
                    )}

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
                            ) : chapters.length === 0 ? (
                                <p className="px-3 py-8 text-center text-sm text-gray-400">
                                    暂无章节，提交「解析基准」后将在此展示章节目录。
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
