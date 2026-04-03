import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Book, GitBranch, AlertCircle, Trash2, X, Database, ChevronRight } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { toast } from 'sonner';

// ---- Sub-component: Version Tag ----
function VersionTag({
                      version,
                      onDelete,
                      isPending,
                    }: {
  version: string;
  onDelete: () => void;
  isPending: boolean;
}) {
  const [confirming, setConfirming] = useState(false);

  if (confirming) {
    return (
        <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-red-50 text-red-600 border border-red-200 animate-in fade-in duration-150">
        <span className="mr-0.5">删除此版本？</span>
        <button
            onClick={() => { onDelete(); setConfirming(false); }}
            disabled={isPending}
            className="px-1.5 py-0.5 rounded bg-red-500 text-white hover:bg-red-600 transition-colors text-[10px] font-semibold"
        >
          {isPending ? <Loader2 className="w-2.5 h-2.5 animate-spin" /> : '确认'}
        </button>
        <button
            onClick={() => setConfirming(false)}
            className="p-0.5 rounded hover:bg-red-100 transition-colors text-red-400 hover:text-red-600"
        >
          <X className="w-3 h-3" />
        </button>
      </span>
    );
  }

  return (
      <span className="group/tag inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-medium bg-indigo-50 text-indigo-700 border border-indigo-100 hover:border-indigo-300 hover:bg-indigo-100 transition-all duration-150 cursor-default select-none">
      <GitBranch className="w-3 h-3 shrink-0 text-indigo-400" />
      <span>{version}</span>
      <button
          onClick={() => setConfirming(true)}
          className="ml-0.5 p-0.5 rounded-full text-indigo-300 hover:text-red-500 hover:bg-white opacity-0 group-hover/tag:opacity-100 transition-all duration-150"
          title="删除版本"
      >
        <X className="w-3 h-3" />
      </button>
    </span>
  );
}

// ---- Sub-component: Novel Card ----
function NovelVersionsCard({ novel }: { novel: string }) {
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

// ---- Page ----
export default function KnowledgePage() {
  const { data: novels, isLoading, isError } = useQuery({
    queryKey: ['novels'],
    queryFn: novelApi.getNovels,
  });

  const novelList = Array.isArray(novels) ? novels : [];

  return (
      <div className="min-h-screen bg-slate-50">
        <div className="max-w-7xl mx-auto px-6 py-10 space-y-8">

          {/* Page header */}
          <div className="flex flex-col gap-3">
            <div className="flex items-center gap-3">
              <div className="p-2 rounded-xl bg-indigo-100">
                <Database className="w-5 h-5 text-indigo-600" />
              </div>
              <h1 className="text-2xl font-bold text-slate-900 tracking-tight">知识库管理</h1>
            </div>

            <p className="text-sm text-slate-500 leading-relaxed max-w-2xl">
              此处展示已成功消费 MQ 队列任务并写入 ChromaDB 向量库的文件。若刚上传但未见到，请前往
              <a
                  href="/ingest"
                  className="inline-flex items-center gap-0.5 mx-1 text-indigo-600 font-medium hover:text-indigo-800 hover:underline underline-offset-2 transition-colors"
              >
                入库处理
                <ChevronRight className="w-3.5 h-3.5" />
              </a>
              页面查看队列状态。
            </p>
          </div>

          {/* Stats bar */}
          {!isLoading && !isError && novelList.length > 0 && (
              <div className="flex items-center gap-2 text-xs text-slate-400">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white border border-slate-200 text-slate-600 font-medium shadow-sm">
              <Book className="w-3.5 h-3.5 text-indigo-400" />
              共 {novelList.length} 部小说
            </span>
              </div>
          )}

          {/* Content */}
          {isLoading ? (
              <div className="flex flex-col items-center justify-center py-24 text-slate-400">
                <Loader2 className="w-8 h-8 animate-spin mb-4 text-indigo-400" />
                <p className="text-sm">正在加载知识库…</p>
              </div>
          ) : isError ? (
              <div className="flex items-center gap-4 p-5 rounded-xl border border-red-200 bg-red-50 text-red-700 max-w-lg">
                <AlertCircle className="w-5 h-5 shrink-0" />
                <div>
                  <p className="font-semibold text-sm">加载失败</p>
                  <p className="text-xs mt-0.5 text-red-500">无法获取小说列表，请确认后端服务已启动。</p>
                </div>
              </div>
          ) : novelList.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-24 text-center">
                <div className="p-4 rounded-2xl bg-slate-100 mb-4">
                  <Database className="w-8 h-8 text-slate-400" />
                </div>
                <p className="text-sm font-medium text-slate-600 mb-1">暂无已入库的小说</p>
                <p className="text-xs text-slate-400">
                  前往
                  <a href="/ingest" className="mx-1 text-indigo-500 hover:underline">
                    入库处理
                  </a>
                  页面上传文件
                </p>
              </div>
          ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {novelList.map((novel) => (
                    <NovelVersionsCard key={novel} novel={novel} />
                ))}
              </div>
          )}
        </div>
      </div>
  );
}