import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import {
  Loader2, FileText, Eye, CheckCircle, ListChecks,
  XCircle, RefreshCw, ClipboardCheck
} from "lucide-react";
import { cn } from "@/lib/utils";
import { SplitPreviewModal } from '@/pages/Ingest/components/SplitPreviewModal';
import { ChapterReviewModal } from '@/pages/Ingest/components/ChapterReviewModal';
import { TaskPollerStatus } from '@/pages/Ingest/components/TaskPollerStatus';
import type { SplitTask } from "@/api/taskApi";

interface ProcessingPanelProps {
  state: {
    currentNovelId: string;
    version: string;
    maxTokens: number;
    overlapTokens: number;
    chapterReviewAck: boolean;
    chapterTitleRegex: string;
    recognitionStrategy: string;
    tasks: SplitTask[];
    activeTasks: SplitTask[];
    poller: {
      errorCount: number;
      isPaused: boolean;
      stuckTaskIds: string[];
      timeoutTaskIds: string[];
    };
    isChapterParsing: boolean;
    isSceneSplitting: boolean;
    isEmbedding: boolean;
  };
  actions: {
    setVersion: (v: string) => void;
    setMaxTokens: (v: number) => void;
    setOverlapTokens: (v: number) => void;
    setChapterTitleRegex: (v: string) => void;
    setRecognitionStrategy: (v: string) => void;
    acknowledgeChapterReview: () => void;
    handleChapterParse: () => void;
    handleSceneSplit: (triggerEmbed: boolean) => void;
    handleForceReparseChapters: () => void;
    handleEmbed: () => void;
    manualRefresh: () => Promise<void>;
    selectNovelById: (novelId: string) => void;
    clearSelectedNovel: () => void;
    addActiveTask: (taskId: string) => void;
  };
}

export function ProcessingPanel({ state, actions }: ProcessingPanelProps) {
  const {
    currentNovelId, version, maxTokens, overlapTokens,
    chapterReviewAck, chapterTitleRegex, recognitionStrategy,
    tasks, activeTasks, poller,
    isChapterParsing, isSceneSplitting, isEmbedding,
  } = state;

  const [previewOpen, setPreviewOpen] = useState(false);
  const [chapterReviewOpen, setChapterReviewOpen] = useState(false);

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
    <div className="rounded-2xl border-2 border-dashed border-indigo-200 bg-gradient-to-br from-indigo-50/60 via-white to-violet-50/40 p-6 relative">

      {/* Current novel selector */}
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

        <div className="space-y-2">
          <p className="text-[11px] text-slate-500">
            从书库选择已上传的小说。先<strong>解析章节</strong>（Load），再<strong>场景切分</strong>（Split）。
            同一本书可用不同场景版本号生成多套切片。
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
              <div className="px-3 py-6 text-center text-sm text-slate-400">
                暂无已登记小说，请前往「上传入库」页面上传文件。
              </div>
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

        {currentNovelId ? (
          <p className="text-[11px] text-slate-500 font-mono break-all border-t border-slate-100 pt-2">
            当前 novelId: {currentNovelId}
          </p>
        ) : null}
      </div>

      {/* Config fields */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-5">
        <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
            识别策略
          </label>
          <select
            value={recognitionStrategy}
            onChange={(e) => actions.setRecognitionStrategy(e.target.value)}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            <option value="PLAIN">普通章节</option>
            <option value="VOLUME_CHAPTER">分卷章节</option>
            <option value="CUSTOM">自定义正则</option>
          </select>
        </div>
        {recognitionStrategy === 'CUSTOM' && (
          <div className="space-y-1.5 sm:col-span-2 lg:col-span-3">
            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">
              章节标题正则（可选）
            </label>
            <input
              type="text"
              value={chapterTitleRegex}
              onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
              placeholder="整行匹配 Java 正则，留空用默认"
              className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
            />
          </div>
        )}
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本标识</label>
          <input
            type="text"
            value={version}
            onChange={(e) => actions.setVersion(e.target.value)}
            placeholder="v1"
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">场景块大小（字）</label>
          <input
            type="number"
            value={maxTokens}
            onChange={(e) => actions.setMaxTokens(Number(e.target.value))}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">重叠（字）</label>
          <input
            type="number"
            value={overlapTokens}
            onChange={(e) => actions.setOverlapTokens(Number(e.target.value))}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
      </div>

      {/* Processing instructions */}
      <div className="space-y-2 rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-3 text-[11px] text-slate-600 leading-relaxed mb-5">
        <p>
          <span className="font-semibold text-slate-700">① 解析章节</span>（CHAPTER_PARSE / Load 队列）：原文 → 章节目录与解析文件，<span className="text-slate-800">不会</span>自动场景切分。
        </p>
        <p>
          <span className="font-semibold text-slate-700">② 场景切分</span>（SCENE_SPLIT / Split 队列）：需小说已处于已解析状态。
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

      {/* Action buttons */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
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
      </div>

      {/* Task polling status */}
      <TaskPollerStatus tasks={activeTasks} poller={poller} onManualRefresh={actions.manualRefresh} />

      {/* Modals */}
      {currentNovelId && (
        <>
          <SplitPreviewModal isOpen={previewOpen} onClose={() => setPreviewOpen(false)} novelId={currentNovelId} />
          <ChapterReviewModal
            open={chapterReviewOpen}
            novelId={currentNovelId}
            version={version}
            onClose={() => setChapterReviewOpen(false)}
            onAcknowledge={actions.acknowledgeChapterReview}
            onReparseTaskCreated={(taskId) => actions.addActiveTask(taskId)}
          />
        </>
      )}
    </div>
  );
}
