import { useState } from 'react';
import { AlertTriangle, Loader2, Trash2 } from "lucide-react";

interface DeleteNovelModalProps {
  novelName: string;
  isPending: boolean;
  deleteDisabled: boolean;
  onClose: () => void;
  onConfirm: (purgeTerminalSplitTasks: boolean) => void;
}

export function DeleteNovelModal({ novelName, isPending, deleteDisabled, onClose, onConfirm }: DeleteNovelModalProps) {
  const [purge, setPurge] = useState(false);

  return (
    <div
      className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4 sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="delete-novel-title"
      onKeyDown={(e) => { if (e.key === 'Escape' && !isPending) onClose(); }}
    >
      <div className="bg-white rounded-2xl w-full max-w-md shadow-2xl p-6">
        <div className="flex items-center gap-3 mb-3">
          <div className="p-2 rounded-xl bg-red-100">
            <AlertTriangle className="w-5 h-5 text-red-600" />
          </div>
          <h2 id="delete-novel-title" className="text-lg font-semibold text-slate-900">确认删除知识库</h2>
        </div>
        <p className="text-sm text-slate-600 mb-1">
          将删除 <span className="font-semibold text-slate-800">{novelName}</span> 的所有源文件、切分版本和向量数据，操作不可恢复。
        </p>
        <label className="flex items-start gap-2 mt-4 mb-5 text-xs text-slate-700 cursor-pointer select-none">
          <input
            type="checkbox"
            className="mt-0.5 rounded border-slate-300 text-red-600 focus:ring-red-400 shrink-0"
            checked={purge}
            onChange={(e) => setPurge(e.target.checked)}
          />
          <span>
            同时删除本书流水线任务表中<span className="font-semibold">已成功/已失败</span>的历史记录（进行中的任务仍会阻止删除）
          </span>
        </label>
        <div className="flex justify-end gap-2">
          <button
            type="button"
            onClick={onClose}
            disabled={isPending}
            className="px-4 py-2 rounded-lg bg-white text-slate-600 text-sm font-medium border border-slate-200 hover:bg-slate-50 transition-colors disabled:opacity-50"
          >
            取消
          </button>
          <button
            type="button"
            onClick={() => onConfirm(purge)}
            disabled={isPending || deleteDisabled}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-red-500 text-white text-sm font-semibold hover:bg-red-600 disabled:opacity-60 transition-colors"
          >
            {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
            确认删除
          </button>
        </div>
      </div>
    </div>
  );
}
