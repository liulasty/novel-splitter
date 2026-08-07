import { useState } from 'react';
import { toast } from 'sonner';
import { Loader2, Play, RefreshCw, Rocket, Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { NovelVersionDto } from '@/api/novelApi';

interface VersionRowProps {
  version: NovelVersionDto;
  isStartingSplit: boolean;
  isStartingEmbed: boolean;
  isActivating: boolean;
  isDeletingVersion: boolean;
  onStartSplit: () => void;
  onStartEmbed: () => void;
  onActivate: () => void;
  onDelete: () => void;
  onReEnrich: () => void;
  onResetEnrich: () => void;
}

const STATUS_META: Record<string, { label: string; className: string }> = {
  PENDING: { label: '待处理', className: 'bg-slate-100 text-slate-600' },
  SPLITTING: { label: '切分中', className: 'bg-blue-100 text-blue-700' },
  SPLIT_DONE: { label: '切分完成', className: 'bg-indigo-100 text-indigo-700' },
  EMBEDDING: { label: '向量化中', className: 'bg-blue-100 text-blue-700' },
  EMBED_DONE: { label: '向量化完成', className: 'bg-indigo-100 text-indigo-700' },
  ACTIVE: { label: '已激活', className: 'bg-emerald-100 text-emerald-700' },
  FAILED: { label: '失败', className: 'bg-red-100 text-red-700' },
  ABANDONED: { label: '已废弃', className: 'bg-slate-200 text-slate-500' },
};

function formatTime(ts?: number | null): string {
  if (!ts) return '—';
  try {
    return new Date(ts).toLocaleString('zh-CN', { hour12: false });
  } catch {
    return '—';
  }
}

export function VersionRow({
  version,
  isStartingSplit,
  isStartingEmbed,
  isActivating,
  isDeletingVersion,
  onStartSplit,
  onStartEmbed,
  onActivate,
  onDelete,
  onReEnrich,
  onResetEnrich,
}: VersionRowProps) {
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const status = version.status || 'PENDING';
  const meta = STATUS_META[status] ?? { label: status, className: 'bg-slate-100 text-slate-600' };
  const enrichProgress = version.enrichProgress ?? null;
  const enrichComplete = version.enrichComplete === true;
  const enrichIntermediate = enrichProgress != null && enrichProgress > 0 && enrichProgress < 100;
  const isProcessing = status === 'SPLITTING' || status === 'EMBEDDING';

  // 续传目标：SPLITTING 续切分，EMBEDDING/FAILED 续向量化（startEmbed 允许 FAILED 重入）
  const resumeTarget = status === 'SPLITTING' ? 'split' : 'embed';
  const resumePending = resumeTarget === 'split' ? isStartingSplit : isStartingEmbed;

  const handleDeleteClick = () => {
    if (version.active) {
      toast.warning('请先激活其它版本以停用当前「检索中」版本，再删除此版本。');
      return;
    }
    setConfirmingDelete((prev) => !prev);
  };

  const confirmDelete = () => {
    setConfirmingDelete(false);
    onDelete();
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 space-y-3">
      {/* Header：版本标识 + 检索中标记 + 状态徽标 */}
      <div className="flex items-center justify-between gap-2 flex-wrap">
        <div className="flex items-center gap-2">
          <span className="font-mono font-semibold text-slate-800">{version.versionTag}</span>
          {version.active && (
            <span className="inline-flex items-center gap-1.5 text-xs font-semibold bg-emerald-100 text-emerald-700 px-2 py-0.5 rounded-full">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
              检索中
            </span>
          )}
        </div>
        <span className={cn('text-xs font-semibold px-2.5 py-0.5 rounded-full', meta.className)}>{meta.label}</span>
        {enrichProgress != null && ['SPLIT_DONE', 'EMBEDDING', 'EMBED_DONE', 'ACTIVE'].includes(status) && (
          enrichProgress === 100 ? (
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-600">
              语义分析完成
            </span>
          ) : enrichProgress === 0 ? (
            <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-500">
              未启动语义分析
            </span>
          ) : (
            <span
              className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-amber-100 text-amber-700"
              title="语义分析进行中：结构化标签与过滤尚不可用，完成后或放弃后方可向量化"
            >
              ⌛ 语义分析中（{enrichProgress}%）
            </span>
          )
        )}
      </div>

      {/* 切分参数 */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
        <span>
          策略：<span className="font-mono text-slate-600">{version.splitStrategy ?? '—'}</span>
        </span>
        <span>
          块大小：<span className="font-mono text-slate-600">{version.chunkSize ?? '—'}</span>
        </span>
        <span>
          重叠：<span className="font-mono text-slate-600">{version.chunkOverlap ?? '—'}</span>
        </span>
      </div>

      {/* 游标进度 */}
      <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-slate-500">
        <span>
          已切 <span className="font-mono text-slate-600">{version.splitCursorChapterIndex ?? 0}</span> 章
        </span>
        <span>
          已向量化 <span className="font-mono text-slate-600">{version.embedCursorSceneSeq ?? 0}</span> 场景
        </span>
      </div>

      {/* collection + 时间 */}
      {version.collectionName ? (
        <div className="text-xs text-slate-400 font-mono truncate" title={version.collectionName}>
          {version.collectionName}
        </div>
      ) : null}
      <div className="text-[11px] text-slate-400">
        创建 {formatTime(version.createdAt)}
        {version.activatedAt ? ` · 激活 ${formatTime(version.activatedAt)}` : ''}
      </div>

      {/* 内联删除确认 */}
      {confirmingDelete && (
        <div className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex-wrap">
          <span>
            确认删除版本 <span className="font-mono font-semibold">{version.versionTag}</span>？
          </span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setConfirmingDelete(false)}
              disabled={isDeletingVersion}
              className="h-7 px-3 rounded-full bg-white text-red-700 border border-red-200 hover:bg-red-100 transition-colors disabled:opacity-50"
            >
              取消
            </button>
            <button
              type="button"
              onClick={confirmDelete}
              disabled={isDeletingVersion}
              className="inline-flex items-center gap-1.5 h-7 px-3 rounded-full bg-red-500 text-white hover:bg-red-600 disabled:opacity-60 transition-colors"
            >
              {isDeletingVersion ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
              确认删除
            </button>
          </div>
        </div>
      )}

      {/* 操作按钮（按 status gate） */}
      <div className="flex gap-2 flex-wrap">
        {status === 'PENDING' && (
          <button
            type="button"
            onClick={onStartSplit}
            disabled={isStartingSplit}
            className="inline-flex items-center gap-1.5 h-8 px-4 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-indigo-600 hover:from-violet-600 hover:to-indigo-700 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {isStartingSplit ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
            发起切分
          </button>
        )}

        {isProcessing && (
          <button
            type="button"
            onClick={resumeTarget === 'split' ? onStartSplit : onStartEmbed}
            disabled={resumePending}
            className="inline-flex items-center gap-1.5 h-8 px-4 rounded-full text-sm font-medium text-white bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {resumePending ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
            续传
          </button>
        )}

        {status === 'SPLIT_DONE' && (
          <button
            type="button"
            onClick={onStartEmbed}
            disabled={isStartingEmbed || enrichIntermediate}
            className="inline-flex items-center gap-1.5 h-8 px-4 rounded-full text-sm font-medium text-white bg-gradient-to-r from-fuchsia-500 to-purple-600 hover:from-fuchsia-600 hover:to-purple-700 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {isStartingEmbed ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
            发起向量化
          </button>
        )}
        {status === 'SPLIT_DONE' && enrichIntermediate && (
          <div className="w-full flex flex-wrap items-center gap-2 text-xs text-amber-600">
            <span>语义分析进行中（{enrichProgress}%），完成后或放弃后方可向量化。</span>
            <button
              type="button"
              onClick={onReEnrich}
              className="h-6 px-2.5 rounded-full border border-amber-300 bg-amber-50 hover:bg-amber-100"
            >
              继续分析
            </button>
            <button
              type="button"
              onClick={onResetEnrich}
              className="h-6 px-2.5 rounded-full border border-red-200 bg-red-50 text-red-600 hover:bg-red-100"
            >
              放弃分析（回退至 0%）
            </button>
          </div>
        )}

        {status === 'ACTIVE' && enrichComplete && (
          <button
            type="button"
            onClick={onStartEmbed}
            disabled={isStartingEmbed}
            title="若此前向量化早于语义抽取，重跑一次可让结构化标签与过滤生效（幂等，会清空并重建该版本向量）"
            className="inline-flex items-center gap-1.5 h-8 px-4 rounded-full text-xs font-medium border border-amber-300 bg-amber-50 text-amber-700 hover:bg-amber-100 transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {isStartingEmbed ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
            重新向量化
          </button>
        )}

        {status === 'EMBED_DONE' && (
          <button
            type="button"
            onClick={onActivate}
            disabled={isActivating}
            className="inline-flex items-center gap-1.5 h-8 px-4 rounded-full text-sm font-semibold text-white bg-gradient-to-r from-emerald-500 to-teal-500 hover:from-emerald-600 hover:to-teal-600 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {isActivating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Rocket className="w-3.5 h-3.5" />}
            激活
          </button>
        )}

        {status === 'FAILED' && (
          <button
            type="button"
            onClick={onStartEmbed}
            disabled={isStartingEmbed}
            className="inline-flex items-center gap-1.5 h-8 px-4 rounded-full text-sm font-medium text-white bg-gradient-to-r from-blue-500 to-indigo-500 hover:from-blue-600 hover:to-indigo-600 hover:shadow transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {isStartingEmbed ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
            续传
          </button>
        )}

        {/* 删除（处理中隐藏；ACTIVE 点击时前端提示需先切回其它版本） */}
        {!isProcessing && (
          <button
            type="button"
            onClick={handleDeleteClick}
            disabled={isDeletingVersion}
            className="inline-flex items-center gap-1.5 h-8 px-3 rounded-full text-xs font-medium border border-slate-200 bg-white text-slate-500 hover:text-red-600 hover:border-red-200 hover:bg-red-50 transition-all disabled:opacity-40 disabled:pointer-events-none"
          >
            {isDeletingVersion ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Trash2 className="w-3.5 h-3.5" />}
            删除
          </button>
        )}
      </div>
    </div>
  );
}
