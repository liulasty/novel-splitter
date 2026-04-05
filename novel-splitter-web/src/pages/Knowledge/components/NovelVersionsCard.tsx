import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Book, Trash2, AlertCircle } from "lucide-react";
import { knowledgeApi } from "@/api/knowledgeApi";
import { toast } from 'sonner';
import { VersionTag } from "./VersionTag";

export function NovelVersionsCard({ novel }: { novel: string }) {
    const queryClient = useQueryClient();
    const [deleteConfirming, setDeleteConfirming] = useState(false);

    const { data: versions, isLoading, isError } = useQuery({
        queryKey: ['versions', novel],
        queryFn: () => knowledgeApi.getVersions(novel),
    });

    const deleteNovelMutation = useMutation({
        mutationFn: () => knowledgeApi.deleteKnowledgeBase(novel),
        onSuccess: () => {
            toast.success(`知识库 "${novel}" 已删除`);
            queryClient.invalidateQueries({ queryKey: ['novels'] });
        },
        onError: (error) => {
            toast.error(`删除知识库失败: ${error}`);
            setDeleteConfirming(false);
        },
    });

    const deleteVersionMutation = useMutation({
        mutationFn: (version: string) => knowledgeApi.deleteVersion(novel, version),
        onSuccess: (_, version) => {
            toast.success(`版本 "${version}" 已删除`);
            queryClient.invalidateQueries({ queryKey: ['versions', novel] });
        },
        onError: (error) => {
            toast.error(`删除版本失败: ${error}`);
        },
    });

    const versionCount = versions?.length ?? 0;

    return (
        <Card className="relative overflow-hidden border border-slate-200 bg-white hover:border-slate-300 hover:shadow-md transition-all duration-200 group">
            {/* Top accent bar */}
            <div className="h-0.5 w-full bg-gradient-to-r from-indigo-400 via-blue-400 to-cyan-400 opacity-60 group-hover:opacity-100 transition-opacity" />

            <CardHeader className="pb-3 pt-4 px-5">
                <div className="flex items-start justify-between gap-2">
                    <div className="flex items-center gap-2.5 min-w-0">
                        <div className="p-1.5 rounded-lg bg-indigo-50 shrink-0">
                            <Book className="w-4 h-4 text-indigo-500" />
                        </div>
                        <CardTitle
                            className="text-base font-semibold text-slate-800 truncate leading-snug"
                            title={novel}
                        >
                            {novel}
                        </CardTitle>
                    </div>

                    {/* Delete button — only show when not confirming */}
                    {!deleteConfirming && (
                        <button
                            onClick={() => setDeleteConfirming(true)}
                            disabled={deleteNovelMutation.isPending}
                            className="shrink-0 p-1.5 rounded-md text-slate-300 hover:text-red-500 hover:bg-red-50 opacity-0 group-hover:opacity-100 transition-all duration-150"
                            title="删除知识库"
                        >
                            <Trash2 className="w-4 h-4" />
                        </button>
                    )}
                </div>

                <CardDescription className="mt-1.5 ml-[2.375rem] text-xs text-slate-400">
                    {isLoading ? (
                        <span className="inline-flex items-center gap-1">
                            <Loader2 className="w-3 h-3 animate-spin" /> 加载中…
                        </span>
                    ) : (
                        <span>{versionCount} 个向量版本</span>
                    )}
                </CardDescription>
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
                                disabled={deleteNovelMutation.isPending}
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
                {isLoading ? (
                    <div className="flex justify-center py-3">
                        <Loader2 className="w-4 h-4 animate-spin text-slate-300" />
                    </div>
                ) : isError ? (
                    <div className="flex items-center gap-2 text-xs text-red-500 bg-red-50 border border-red-100 px-3 py-2 rounded-lg">
                        <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                        获取版本列表失败
                    </div>
                ) : versions && versions.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                        {versions.map((v) => (
                            <VersionTag
                                key={v}
                                version={v}
                                onDelete={() => deleteVersionMutation.mutate(v)}
                                isPending={deleteVersionMutation.isPending}
                            />
                        ))}
                    </div>
                ) : (
                    <p className="text-xs text-slate-400 italic">暂无版本数据</p>
                )}
            </CardContent>
        </Card>
    );
}