import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { novelApi } from '@/api/novelApi';
import { Loader2, BookOpen, X, CheckCircle } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';
import { getApiErrorMessage } from '@/lib/apiError';

interface ChapterReviewModalProps {
  open: boolean;
  novelId: string;
  version: string;
  onClose: () => void;
  /** 用户确认章节目录无误，允许场景切分 */
  onAcknowledge: () => void;
  onReparseTaskCreated?: (taskId: string) => void;
}

export function ChapterReviewModal({
  open,
  novelId,
  version,
  onClose,
  onAcknowledge,
  onReparseTaskCreated,
}: ChapterReviewModalProps) {
  const queryClient = useQueryClient();
  const [regex, setRegex] = useState('');
  const [reparsePending, setReparsePending] = useState(false);

  const { data: chapters = [], isLoading } = useQuery({
    queryKey: ['chapters', novelId],
    queryFn: () => novelApi.getChapters(novelId),
    enabled: open && !!novelId,
  });

  const handleReparse = async () => {
    setReparsePending(true);
    try {
      const data = await novelApi.reparseChapters(novelId, {
        version,
        maxScenes: 0,
        ...(regex.trim() !== '' ? { chapterTitleRegex: regex.trim() } : {}),
      });
      toast.success(data.message ?? '已提交章节重解析');
      if (data.taskId) onReparseTaskCreated?.(data.taskId);
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      queryClient.invalidateQueries({ queryKey: ['chapters', novelId] });
    } catch (e: unknown) {
      toast.error(getApiErrorMessage(e, '提交失败'));
    } finally {
      setReparsePending(false);
    }
  };

  const handleConfirm = () => {
    onAcknowledge();
    toast.success('已确认章节目录，可进行场景切分');
    onClose();
  };

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[60] bg-black/50 flex items-center justify-center p-4 sm:p-6">
      <div
        className="bg-white rounded-2xl w-full max-w-2xl max-h-[85vh] shadow-2xl flex flex-col overflow-hidden border border-slate-200"
        role="dialog"
        aria-modal="true"
        aria-labelledby="chapter-review-title"
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-100 bg-slate-50/80">
          <h2 id="chapter-review-title" className="text-lg font-semibold text-slate-900 flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-indigo-600" />
            章节校对
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-full transition-colors"
            aria-label="关闭"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="px-5 py-4 space-y-4 overflow-y-auto flex-1">
          <p className="text-sm text-slate-600 leading-relaxed">
            请核对左侧章节列表与正文是否一致。若分章不准，可填写<strong>整行匹配</strong>的 Java 正则后点击「按正则重解析」（会清理旧章节产物后重新 Load）。
          </p>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">章节标题正则（可选）</label>
            <input
              type="text"
              value={regex}
              onChange={(e) => setRegex(e.target.value)}
              placeholder="例如：^第\\d+章.*"
              className="w-full h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm font-mono text-slate-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <button
              type="button"
              disabled={reparsePending || !novelId}
              onClick={() => void handleReparse()}
              className={cn(
                'inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium',
                'bg-amber-500 text-white hover:bg-amber-600 disabled:opacity-40 disabled:pointer-events-none'
              )}
            >
              {reparsePending ? <Loader2 className="w-4 h-4 animate-spin" /> : null}
              按正则重解析
            </button>
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50/50 overflow-hidden">
            <div className="px-3 py-2 border-b border-slate-200 bg-white/80 text-xs font-semibold text-slate-600">
              当前章节（{chapters.length}）
            </div>
            <div className="max-h-64 overflow-y-auto divide-y divide-slate-100">
              {isLoading ? (
                <div className="flex justify-center py-10">
                  <Loader2 className="w-6 h-6 animate-spin text-slate-400" />
                </div>
              ) : chapters.length === 0 ? (
                <div className="px-3 py-8 text-center text-sm text-slate-500">暂无章节，请先执行「解析章节」。</div>
              ) : (
                chapters.map((ch) => (
                  <div key={ch.id} className="px-3 py-2.5 text-sm flex flex-col sm:flex-row sm:items-center sm:justify-between gap-1 bg-white">
                    <span className="text-slate-900 font-medium truncate">
                      <span className="text-slate-400 font-mono text-xs mr-2">{ch.index}</span>
                      {ch.title}
                    </span>
                    <span className="text-xs text-slate-500 shrink-0 font-mono">
                      行 {ch.startParagraphIndex}–{ch.endParagraphIndex} · {ch.paragraphCount ?? '—'} 行
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-end gap-2 px-5 py-4 border-t border-slate-100 bg-white">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-lg text-sm font-medium text-slate-600 hover:bg-slate-100"
          >
            关闭
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700"
          >
            <CheckCircle className="w-4 h-4" />
            确认无误，允许场景切分
          </button>
        </div>
      </div>
    </div>
  );
}
