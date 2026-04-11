import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Loader2, Book, AlertCircle, Database, ChevronRight } from "lucide-react";
import { novelApi, type NovelSummaryDto } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { NovelVersionsCard } from "./Knowledge/components/NovelVersionsCard";
import {
  novelKnowledgePhase,
  KNOWLEDGE_SECTION_ORDER,
  sectionTitleForPhase,
  sectionHintForPhase,
  type NovelKnowledgePhase,
} from "./Knowledge/novelKnowledgePhase";

function groupNovelsByPhase(novels: NovelSummaryDto[]): Map<NovelKnowledgePhase, NovelSummaryDto[]> {
  const map = new Map<NovelKnowledgePhase, NovelSummaryDto[]>();
  for (const phase of KNOWLEDGE_SECTION_ORDER) {
    map.set(phase, []);
  }
  for (const n of novels) {
    const phase = novelKnowledgePhase(n.status);
    const list = map.get(phase);
    if (list) list.push(n);
    else {
      const u = map.get('unknown')!;
      u.push(n);
    }
  }
  return map;
}

export default function KnowledgePage() {
  const { data: novels, isLoading, isError } = useQuery({
    queryKey: ['novelSummaries', 'all'],
    queryFn: () => novelApi.getNovelSummaries('all'),
  });
  const { data: tasks = [] } = useQuery({
    queryKey: ['tasks'],
    queryFn: taskApi.getAllTasks,
    refetchInterval: 5000,
  });

  const novelList = Array.isArray(novels) ? novels : [];
  const runningNovelIds = new Set(
    tasks
      .filter(task => task.status === 'PENDING' || task.status === 'PROCESSING')
      .map(task => task.novelId)
      .filter((novelId): novelId is string => Boolean(novelId))
  );

  const byPhase = useMemo(() => groupNovelsByPhase(novelList), [novelList]);
  const readyCount = byPhase.get('ready')?.length ?? 0;
  const nonReadyCount = novelList.length - readyCount;

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
              <Link to="/ingest" className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg shadow-sm hover:bg-indigo-700 transition-colors">
                <Book className="w-4 h-4" />
                + 新增小说
              </Link>
            </div>

            <p className="text-sm text-slate-500 leading-relaxed max-w-3xl">
              本页拉取<strong className="text-slate-600 font-medium">全部已登记书目</strong>并按状态分区，便于在同一页看到「已上传但未跑完流水线」的书。
              <strong className="text-slate-600 font-medium">对话页</strong>里可选的书与 RAG 调试页一致，仅包含<strong className="text-slate-600 font-medium">向量化已完成</strong>（后端状态 COMPLETED）的书目。
              刚上传尚未切分时会出现于「等待切分 / 解析」分区，请从
              <Link to="/ingest" className="inline-flex items-center gap-0.5 mx-1 text-indigo-600 font-medium hover:text-indigo-800 hover:underline underline-offset-2 transition-colors">
                入库处理<ChevronRight className="w-3.5 h-3.5" />
              </Link>
              继续（链接可带 novelId 深链）。
            </p>
          </div>

          {/* Stats bar */}
          {!isLoading && !isError && novelList.length > 0 && (
                  <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500">
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-white border border-slate-200 text-slate-600 font-medium shadow-sm">
                  <Book className="w-3.5 h-3.5 text-indigo-400" />
                  共 {novelList.length} 部
                </span>
                <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-50 border border-emerald-200 text-emerald-800 font-medium">
                  可检索 {readyCount}
                </span>
                {nonReadyCount > 0 && (
                  <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-amber-50 border border-amber-200 text-amber-900 font-medium">
                    待完成流程 {nonReadyCount}
                  </span>
                )}
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
                    <p className="text-sm font-medium text-slate-600 mb-1">暂无已登记的小说</p>
                    <p className="text-xs text-slate-400">
                      前往<Link to="/ingest" className="mx-1 text-indigo-500 hover:underline">入库处理</Link>页面上传文件
                    </p>
                  </div>
              ) : (
                  <div className="space-y-10">
                    {KNOWLEDGE_SECTION_ORDER.map((phase) => {
                      const items = byPhase.get(phase) ?? [];
                      if (items.length === 0) return null;
                      return (
                        <section key={phase} className="space-y-3">
                          <div className="border-b border-slate-200 pb-2">
                            <h2 className="text-sm font-semibold text-slate-800">
                              {sectionTitleForPhase(phase)}
                              <span className="ml-2 font-normal text-slate-400">({items.length})</span>
                            </h2>
                            <p className="text-xs text-slate-500 mt-1">{sectionHintForPhase(phase)}</p>
                          </div>
                          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                            {items.map((novel) => (
                              <NovelVersionsCard
                                key={novel.novelId}
                                novelId={novel.novelId}
                                novelName={novel.title}
                                hasRunningTasks={runningNovelIds.has(novel.novelId)}
                                status={novel.status}
                              />
                            ))}
                          </div>
                        </section>
                      );
                    })}
                  </div>
            )}
        </div>
      </div>
  );
}
