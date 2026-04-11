import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Link } from 'react-router-dom';
import { Loader2, Book, Trash2, AlertCircle, FileInput } from "lucide-react";
import { knowledgeApi } from "@/api/knowledgeApi";
import { novelApi } from "@/api/novelApi";
import { toast } from 'sonner';
import { getApiErrorMessage, handleConflict409 } from "@/lib/apiError";
import { VersionTag } from "./VersionTag";
import { novelKnowledgePhase, type NovelKnowledgePhase } from "../novelKnowledgePhase";

function accentClassForPhase(phase: NovelKnowledgePhase): string {
    switch (phase) {
        case 'ready':
            return 'from-indigo-400 via-blue-400 to-cyan-400';
        case 'awaitingSplit':
            return 'from-amber-400 via-orange-300 to-amber-500';
        case 'awaitingEmbed':
            return 'from-sky-400 via-cyan-400 to-teal-400';
        case 'processing':
            return 'from-violet-400 via-indigo-400 to-purple-400';
        case 'failed':
            return 'from-red-400 via-rose-400 to-red-500';
        default:
            return 'from-slate-300 via-slate-400 to-slate-500';
    }
}

function phaseBadge(phase: NovelKnowledgePhase): { label: string; className: string } {
    switch (phase) {
        case 'ready':
            return { label: '可检索', className: 'bg-emerald-50 text-emerald-800 border-emerald-200' };
        case 'awaitingSplit':
            return { label: '等待切分', className: 'bg-amber-50 text-amber-900 border-amber-200' };
        case 'awaitingEmbed':
            return { label: '待向量化', className: 'bg-sky-50 text-sky-900 border-sky-200' };
        case 'processing':
            return { label: '处理中', className: 'bg-violet-50 text-violet-900 border-violet-200' };
        case 'failed':
            return { label: '失败', className: 'bg-red-50 text-red-800 border-red-200' };
        default:
            return { label: '未知', className: 'bg-slate-100 text-slate-700 border-slate-200' };
    }
}

export function NovelVersionsCard({
    novelId,
    novelName,
    hasRunningTasks,
    status,
}: {
    novelId: string,
    novelName: string,
    hasRunningTasks: boolean,
    status?: string | null,
}) {
    const queryClient = useQueryClient();
    const [deleteConfirming, setDeleteConfirming] = useState(false);
    const phase = novelKnowledgePhase(status);
    const fetchVersions = phase !== 'awaitingSplit';

    useEffect(() => {
        setDeleteConfirming(false);
    }, [novelId]);

    const { data: versions, isLoading, isError } = useQuery({
        queryKey: ['versions', novelId],
        queryFn: () => knowledgeApi.getVersionsByNovelId(novelId),
        enabled: fetchVersions,
    });

    const deleteNovelMutation = useMutation({
        mutationFn: async () => {
            const cleanupTaskId = await knowledgeApi.deleteKnowledgeBaseById(novelId);
            await novelApi.softDeleteNovel(novelId);
            return cleanupTaskId;
        },
        onSuccess: (cleanupTaskId) => {
            toast.success(`知识库 "${novelName}" 已删除，清理任务：${cleanupTaskId}`);
            queryClient.invalidateQueries({ queryKey: ['novelSummaries'] });
        },
        onError: (error: any) => {
            if (handleConflict409(error, '当前小说存在运行中任务，请等待任务完成后再删除知识库/版本')) {
                setDeleteConfirming(false);
                queryClient.invalidateQueries({ queryKey: ['tasks'] });
                return;
            }
            toast.error(`删除知识库失败: ${getApiErrorMessage(error, '删除知识库失败')}`);
            setDeleteConfirming(false);
        },
    });

    const deleteVersionMutation = useMutation({
        mutationFn: (version: string) => knowledgeApi.deleteVersionByNovelId(novelId, version),
        onSuccess: (_, version) => {
            toast.success(`版本 "${version}" 已删除`);
            queryClient.invalidateQueries({ queryKey: ['versions', novelId] });
        },
        onError: (error: any) => {
            if (handleConflict409(error, '当前小说存在运行中任务，请等待任务完成后再删除版本')) {
                queryClient.invalidateQueries({ queryKey: ['tasks'] });
                return;
            }
            toast.error(`删除版本失败: ${getApiErrorMessage(error, '删除版本失败')}`);
        },
    });

    const versionCount = versions?.length ?? 0;
    const badge = phaseBadge(phase);
    const ingestLink = `/ingest?novelId=${encodeURIComponent(novelId)}`;
    const secondaryTaskLink = '/tasks/pipeline';

    return (
        <Card className="relative overflow-hidden border border-slate-200 bg-white hover:border-slate-300 hover:shadow-md transition-all duration-200 group">
            {/* Top accent bar */}
            <div className={`h-0.5 w-full bg-gradient-to-r ${accentClassForPhase(phase)} opacity-60 group-hover:opacity-100 transition-opacity`} />

            <CardHeader className="pb-3 pt-4 px-5">
                <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2.5 min-w-0">
                        <div className="p-1.5 rounded-lg bg-indigo-50 shrink-0">
                            <Book className="w-4 h-4 text-indigo-500" />
                        </div>
                        <div className="min-w-0 flex flex-col gap-1">
                            <CardTitle
                                className="text-base font-semibold text-slate-800 truncate leading-snug"
                                title={novelName}
                            >
                                {novelName}
                            </CardTitle>
                            <span
                                className={`inline-flex w-fit items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${badge.className}`}
                            >
                                {badge.label}
                            </span>
                        </div>
                    </div>

                    {/* Delete button — only show when not confirming */}
                    {!deleteConfirming && (
                        <button
                            onClick={() => setDeleteConfirming(true)}
                            disabled={deleteNovelMutation.isPending || hasRunningTasks}
                            className="shrink-0 p-1.5 rounded-md text-slate-300 hover:text-red-500 hover:bg-red-50 opacity-0 group-hover:opacity-100 transition-all duration-150 disabled:opacity-40 disabled:cursor-not-allowed"
                            title={hasRunningTasks ? "存在运行中任务，暂不可删除" : "删除知识库"}
                        >
                            <Trash2 className="w-4 h-4" />
                        </button>
                    )}
                </div>

                <CardDescription className="mt-1.5 ml-[2.375rem] text-xs text-slate-500">
                    {!fetchVersions ? (
                        <span className="text-slate-500">
                            尚未完成切分，不产生向量版本；与对话页「已就绪书目」不是同一类展示。
                        </span>
                    ) : isLoading ? (
                        <span className="inline-flex items-center gap-1 text-slate-400">
                            <Loader2 className="w-3 h-3 animate-spin" /> 加载版本…
                        </span>
                    ) : phase === 'ready' ? (
                        <span className="text-slate-600">{versionCount} 个向量版本</span>
                    ) : (
                        <span className="text-slate-500">
                            {versionCount > 0
                                ? `${versionCount} 个版本（${phase === 'awaitingEmbed' ? '待写入向量库' : '处理流程中'}）`
                                : '暂无向量版本'}
                        </span>
                    )}
                </CardDescription>
                <div className="mt-2 ml-[2.375rem] flex flex-wrap gap-x-3 gap-y-1">
                    <Link
                        to={ingestLink}
                        className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 hover:text-indigo-800 hover:underline"
                    >
                        <FileInput className="w-3.5 h-3.5" />
                        {phase === 'ready' ? '继续入库 / 维护' : '去入库处理'}
                    </Link>
                    {(phase === 'awaitingSplit' || phase === 'awaitingEmbed' || phase === 'failed' || phase === 'processing') && (
                        <Link
                            to={secondaryTaskLink}
                            className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-slate-800 hover:underline"
                        >
                            任务 / Pipeline
                        </Link>
                    )}
                </div>
            </CardHeader>

            <CardContent className="px-5 pb-5 pt-0">
                {/* Inline delete confirmation */}
                {deleteConfirming && (
                    <div className="mb-3 p-3 rounded-lg bg-red-50 border border-red-200 animate-in fade-in slide-in-from-top-1 duration-200">
                        <p className="text-sm font-medium text-red-700 mb-0.5">确认删除此知识库？</p>
                        <p className="text-xs text-red-500 mb-3">
                            将删除所有源文件、切分版本和向量数据，操作不可恢复。
                        </p>
                        <div className="flex gap-2">
                            <button
                                onClick={() => deleteNovelMutation.mutate()}
                                disabled={deleteNovelMutation.isPending || hasRunningTasks}
                                className="flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-red-500 text-white text-xs font-semibold hover:bg-red-600 disabled:opacity-60 transition-colors"
                            >
                                {deleteNovelMutation.isPending ? (
                                    <Loader2 className="w-3 h-3 animate-spin" />
                                ) : (
                                    <Trash2 className="w-3 h-3" />
                                )}
                                确认删除
                            </button>
                            <button
                                onClick={() => setDeleteConfirming(false)}
                                disabled={deleteNovelMutation.isPending}
                                className="px-3 py-1.5 rounded-md bg-white text-slate-600 text-xs font-medium border border-slate-200 hover:bg-slate-50 transition-colors"
                            >
                                取消
                            </button>
                        </div>
                    </div>
                )}

                {/* Version list */}
                {!fetchVersions ? (
                    <p className="text-xs text-slate-400 leading-relaxed">
                        完成 Load（若需要）与切分后，此处会列出可向量化的版本。
                    </p>
                ) : isLoading ? (
                    <div className="flex justify-center py-3">
                        <Loader2 className="w-4 h-4 animate-spin text-slate-300" />
                    </div>
                ) : isError ? (
                    <div className="flex items-center gap-2 text-xs text-red-500 bg-red-50 border border-red-100 px-3 py-2 rounded-lg">
                        <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                        获取版本列表失败
                    </div>
                ) : versions && versions.length > 0 ? (
                    <div className="flex flex-col gap-2">
                        {versions.map((v) => (
                            <VersionTag
                                key={v}
                                version={v}
                                onDelete={() => deleteVersionMutation.mutate(v)}
                                isPending={deleteVersionMutation.isPending}
                                disabled={hasRunningTasks}
                                stat={undefined}
                            />
                        ))}
                    </div>
                ) : (
                    <p className="text-xs text-slate-400 italic">
                        {phase === 'ready' ? '暂无版本数据' : '尚无可用版本列表'}
                    </p>
                )}
            </CardContent>
        </Card>
    );
}