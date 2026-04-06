import { useQuery } from '@tanstack/react-query';
import { Loader2, Book, AlertCircle, Database, ChevronRight } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { NovelVersionsCard } from "./Knowledge/components/NovelVersionsCard";

export default function KnowledgePage() {
  const { data: novels, isLoading, isError } = useQuery({
    queryKey: ['novels'],
    queryFn: novelApi.getNovels,
  });

  const { data: stats } = useQuery({
    queryKey: ['novelStats'],
    queryFn: novelApi.getNovelStats,
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
              <a href="/ingest" className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg shadow-sm hover:bg-indigo-700 transition-colors">
                <Book className="w-4 h-4" />
                + 新增小说
              </a>
            </div>

            <p className="text-sm text-slate-500 leading-relaxed max-w-2xl">
              此处展示已成功消费 MQ 队列任务并写入 ChromaDB 向量库的文件。若刚上传但未见到，请前往
              <a href="/ingest" className="inline-flex items-center gap-0.5 mx-1 text-indigo-600 font-medium hover:text-indigo-800 hover:underline underline-offset-2 transition-colors">
                入库处理<ChevronRight className="w-3.5 h-3.5" />
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
        </div>
      </div>
  );
}