import { CheckCircle, Loader2 } from "lucide-react";
import type { ProcessState, ProcessActions } from './ProcessTypes';

interface EmbedTabProps {
  state: ProcessState;
  actions: ProcessActions;
}

export function EmbedTab({ state, actions }: EmbedTabProps) {
  const { currentNovelId, version, currentProfile, isEmbedding } = state;

  return (
    <div className="space-y-5">
      {/* 只读摘要 */}
      <div className="rounded-xl border border-slate-200 bg-slate-50/50 px-4 py-3">
        {currentProfile ? (
          <p className="text-sm text-slate-600">
            将向量化 <span className="font-mono font-semibold text-slate-800">{version}</span>
            （块大小 {currentProfile.chunkSize} · 重叠 {currentProfile.chunkOverlap}）
          </p>
        ) : (
          <p className="text-sm text-amber-700">
            版本 <span className="font-mono font-semibold">{version || '（未选择）'}</span> 尚未切分完成，
            请先到「场景切分」生成数据集。
          </p>
        )}
      </div>

      {/* 操作按钮 */}
      <div className="flex gap-3 justify-center items-center flex-wrap">
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
    </div>
  );
}
