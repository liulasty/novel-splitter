import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader2, Book, AlertCircle, Database, ChevronRight, List } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { NovelVersionsCard } from "./Knowledge/components/NovelVersionsCard";
import { cn } from "@/lib/utils";

export default function KnowledgePage() {
  const [viewMode, setViewMode] = useState<'novel' | 'vector'>('novel');
  const [page, setPage] = useState(0);
  const size = 12;

  const { data: novels, isLoading, isError } = useQuery({
    queryKey: ['novels'],
    queryFn: novelApi.getNovels,
  });

  const { data: stats } = useQuery({
    queryKey: ['novelStats'],
    queryFn: novelApi.getNovelStats,
  });

  const { data: vectorPage, isLoading: isVectorLoading } = useQuery({
    queryKey: ['lightweightScenes', page, size],
    queryFn: () => knowledgeApi.getLightweightScenes(page, size),
    enabled: viewMode === 'vector',
  });

  const novelList = Array.isArray(novels) ? novels : [];

  return (
      <div className="min-h-screen bg-slate-50">
        <div className="max-w-7xl mx-auto px-6 py-10 space-y-8">

          {/* Page header */}
          <div className="flex flex-col gap-3">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <div className="p-2 rounded-xl bg-indigo-100">
                  <Database className="w-5 h-5 text-indigo-600" />
                </div>
                <h1 className="text-2xl font-bold text-slate-900 tracking-tight">知识库管理</h1>
              </div>

              {/* View Toggle */}
              <div className="flex bg-white rounded-lg border border-gray-200 p-1 shadow-sm">
                <button
                  onClick={() => setViewMode('novel')}
                  className={cn("flex items-center gap-2 px-4 py-1.5 rounded-md text-sm font-medium transition-colors", viewMode === 'novel' ? "bg-indigo-50 text-indigo-700" : "text-gray-500 hover:text-gray-700")}
                >
                  <Book className="w-4 h-4" /> 按小说查看
                </button>
                <button
                  onClick={() => { setViewMode('vector'); setPage(0); }}
                  className={cn("flex items-center gap-2 px-4 py-1.5 rounded-md text-sm font-medium transition-colors", viewMode === 'vector' ? "bg-indigo-50 text-indigo-700" : "text-gray-500 hover:text-gray-700")}
                >
                  <List className="w-4 h-4" /> 全局向量浏览
                </button>
              </div>
            </div>

            {viewMode === 'novel' && (
              <p className="text-sm text-slate-500 leading-relaxed max-w-2xl">
                此处展示已成功消费 MQ 队列任务并写入 ChromaDB 向量库的文件。若刚上传但未见到，请前往
                <a href="/ingest" className="inline-flex items-center gap-0.5 mx-1 text-indigo-600 font-medium hover:text-indigo-800 hover:underline underline-offset-2 transition-colors">
                  入库处理<ChevronRight className="w-3.5 h-3.5" />
                </a>
                页面查看队列状态。
              </p>
            )}
          </div>

          {viewMode === 'novel' ? (
            <>
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
                      前往<a href="/ingest" className="mx-1 text-indigo-500 hover:underline">入库处理</a>页面上传文件
                    </p>
                  </div>
              ) : (
                  <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                    {novelList.map((novel) => (
                        <NovelVersionsCard 
                            key={novel} 
                            novel={novel} 
                            stats={stats?.filter(s => s.novelId === novel) || []} 
                        />
                    ))}
                  </div>
              )}
            </>
          ) : (
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
              <div className="p-4 border-b border-gray-100 bg-gray-50 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-gray-700">轻量级全局向量检索 (Top 150字符)</h3>
                <span className="text-xs text-gray-500">共 {vectorPage?.totalElements || 0} 条记录</span>
              </div>
              
              {isVectorLoading ? (
                <div className="flex flex-col items-center justify-center py-24 text-slate-400">
                  <Loader2 className="w-8 h-8 animate-spin mb-4 text-indigo-400" />
                  <p className="text-sm">正在加载向量数据…</p>
                </div>
              ) : vectorPage?.content.length === 0 ? (
                <div className="py-16 text-center text-gray-500 text-sm">暂无向量数据</div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {vectorPage?.content.map((scene) => (
                    <div key={scene.id} className="p-4 hover:bg-gray-50 transition-colors">
                      <div className="flex items-center gap-3 mb-2">
                        <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-50 text-blue-700 border border-blue-100">
                          {scene.novelId} ({scene.version})
                        </span>
                        {scene.chapterTitle && (
                          <span className="text-xs font-medium text-gray-600 truncate max-w-[200px]">{scene.chapterTitle}</span>
                        )}
                        <span className="text-xs text-gray-400">段落 #{scene.sceneIndex}</span>
                      </div>
                      <p className="text-sm text-gray-600 leading-relaxed line-clamp-2">
                        {scene.text}
                        {scene.text?.length >= 150 && <span className="text-gray-400 ml-1">...</span>}
                      </p>
                    </div>
                  ))}
                </div>
              )}

              {/* Pagination */}
              {vectorPage && vectorPage.totalPages > 1 && (
                <div className="p-4 border-t border-gray-100 bg-gray-50 flex items-center justify-between">
                  <span className="text-xs text-gray-500">
                    第 {page + 1} 页 / 共 {vectorPage.totalPages} 页
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setPage(p => Math.max(0, p - 1))}
                      disabled={page === 0}
                      className="px-3 py-1.5 rounded border border-gray-200 bg-white text-xs font-medium hover:bg-gray-50 disabled:opacity-50"
                    >
                      上一页
                    </button>
                    <button
                      onClick={() => setPage(p => Math.min(vectorPage.totalPages - 1, p + 1))}
                      disabled={page === vectorPage.totalPages - 1}
                      className="px-3 py-1.5 rounded border border-gray-200 bg-white text-xs font-medium hover:bg-gray-50 disabled:opacity-50"
                    >
                      下一页
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
  );
}