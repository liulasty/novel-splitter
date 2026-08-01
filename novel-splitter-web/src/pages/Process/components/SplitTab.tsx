import { useState } from 'react';
import { Loader2, FileText, Eye } from "lucide-react";
import { SplitPreviewModal } from '@/pages/Ingest/components/SplitPreviewModal';
import { splitProfileLabel } from '@/api/knowledgeApi';
import type { ProcessState, ProcessActions, ProcessGates } from './ProcessTypes';

interface SplitTabProps {
  state: ProcessState;
  actions: ProcessActions;
  gates: ProcessGates;
  currentNovelStatus?: string;
}

export function SplitTab({ state, actions, gates, currentNovelStatus }: SplitTabProps) {
  const {
    currentNovelId, version, profiles, currentProfile, maxTokens, overlapTokens,
    chapterReviewAck, isSceneSplitting,
  } = state;
  const [previewOpen, setPreviewOpen] = useState(false);

  return (
    <div className="space-y-5">
      {/* 版本 + chunk 参数 */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本标识</label>
          <select
            value={profiles.some((p) => p.version === version) ? version : ''}
            onChange={(e) => {
              const v = e.target.value;
              if (!v) return;
              actions.setVersion(v);
              const p = profiles.find((x) => x.version === v);
              if (p && p.chunkSize != null) actions.setMaxTokens(p.chunkSize);
              if (p && p.chunkOverlap != null) actions.setOverlapTokens(p.chunkOverlap);
            }}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            <option value="">{profiles.length ? '选择已有版本…' : '暂无已生成版本'}</option>
            {profiles.map((p) => (
              <option key={p.version} value={p.version}>{splitProfileLabel(p)}</option>
            ))}
          </select>
          <input
            type="text"
            value={version}
            onChange={(e) => actions.setVersion(e.target.value)}
            placeholder="或输入新版本名，如 v2"
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
          {currentProfile && (
            <p className="text-[11px] text-slate-400">
              已选数据集：块大小 {currentProfile.chunkSize} · 重叠 {currentProfile.chunkOverlap}
            </p>
          )}
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

      {/* 状态提示 */}
      {currentNovelId && (
        <p className="rounded-xl border border-slate-200 bg-slate-50/50 px-3 py-2 text-[11px] text-slate-600 leading-relaxed">
          当前书状态：<span className="font-mono text-slate-700">{currentNovelStatus ?? '（列表外会话）'}</span>
          {gates.chapterParseBusy ? ' · 章节任务进行中…' : null}
          {gates.structurallyReady && !chapterReviewAck && !gates.chapterParseBusy ? (
            <span> · 请打开「章节校对」并确认无误后再场景切分</span>
          ) : null}
          {!gates.structurallyReady && !gates.chapterParseBusy ? ' · 完成章节解析后可校对并场景切分' : null}
        </p>
      )}

      {/* 操作按钮 */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
        <button
          type="button"
          onClick={() => actions.handleSceneSplit(false)}
          disabled={!currentNovelId || !gates.canSceneSplit || isSceneSplitting}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-indigo-600 hover:from-violet-600 hover:to-indigo-700 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          {isSceneSplitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
          ② 场景切分
        </button>
        <button
          type="button"
          onClick={() => actions.handleSceneSplit(true)}
          disabled={!currentNovelId || !gates.canSceneSplit || isSceneSplitting}
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
      </div>

      {/* 切分预览 Modal（状态在此持有） */}
      {currentNovelId && (
        <SplitPreviewModal isOpen={previewOpen} onClose={() => setPreviewOpen(false)} novelId={currentNovelId} version={version} />
      )}
    </div>
  );
}
