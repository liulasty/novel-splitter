import { useState } from 'react';
import { Loader2, FileText, RefreshCw, ClipboardCheck } from "lucide-react";
import { ChapterReviewModal } from '@/pages/Ingest/components/ChapterReviewModal';
import type { ProcessState, ProcessActions, ProcessGates } from './ProcessTypes';

interface ParseTabProps {
  state: ProcessState;
  actions: ProcessActions;
  gates: ProcessGates;
  currentNovelStatus?: string;
}

export function ParseTab({ state, actions, gates, currentNovelStatus }: ParseTabProps) {
  const { currentNovelId, recognitionStrategy, chapterTitleRegex, version, isChapterParsing } = state;
  const [chapterReviewOpen, setChapterReviewOpen] = useState(false);

  return (
    <div className="space-y-5">
      {/* 识别策略 + 正则 */}
      <div className="grid grid-cols-1 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">识别策略</label>
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
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">章节标题正则（可选）</label>
            <input
              type="text"
              value={chapterTitleRegex}
              onChange={(e) => actions.setChapterTitleRegex(e.target.value)}
              placeholder="整行匹配 Java 正则，留空用默认"
              className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
            />
          </div>
        )}
      </div>

      {/* 状态提示 */}
      {currentNovelId && (
        <p className="rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-2 text-[11px] text-slate-600 leading-relaxed">
          当前书状态：<span className="font-mono text-slate-700">{currentNovelStatus ?? '（列表外会话）'}</span>
          {gates.chapterParseBusy ? ' · 章节任务进行中…' : null}
          {!gates.structurallyReady && !gates.chapterParseBusy ? ' · 完成章节解析后可校对并场景切分' : null}
        </p>
      )}

      {/* 操作按钮 */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
        <button
          type="button"
          onClick={actions.handleChapterParse}
          disabled={!currentNovelId || isChapterParsing || gates.chapterParseBusy}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-600 hover:to-orange-600 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          {isChapterParsing || gates.chapterParseBusy ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
          ① 解析章节
        </button>
        <button
          type="button"
          onClick={actions.handleForceReparseChapters}
          disabled={!currentNovelId || gates.chapterParseBusy}
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
      </div>

      {/* 章节校对 Modal（状态在此持有） */}
      {currentNovelId && (
        <ChapterReviewModal
          open={chapterReviewOpen}
          novelId={currentNovelId}
          version={version}
          onClose={() => setChapterReviewOpen(false)}
          onAcknowledge={actions.acknowledgeChapterReview}
          onReparseTaskCreated={(taskId) => actions.addActiveTask(taskId)}
        />
      )}
    </div>
  );
}
