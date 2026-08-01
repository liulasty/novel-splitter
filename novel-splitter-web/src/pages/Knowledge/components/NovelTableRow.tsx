import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ChevronDown, ChevronRight, FileInput, Loader2, Trash2, AlertCircle } from "lucide-react";
import { knowledgeApi, splitProfileLabel, type SceneSplitProfileDto } from "@/api/knowledgeApi";
import type { NovelSummaryDto, NovelStatRecordDto } from "@/api/novelApi";
import { toast } from 'sonner';
import { format } from 'date-fns';
import { getApiErrorMessage, handleConflict409 } from "@/lib/apiError";
import { novelKnowledgePhase, phaseBadge } from "../novelKnowledgePhase";
import { VersionTag } from "./VersionTag";

interface NovelTableRowProps {
  novel: NovelSummaryDto;
  hasRunningTasks: boolean;
  stats?: NovelStatRecordDto;
  expanded: boolean;
  onToggleExpand: () => void;
  onDelete: (novel: NovelSummaryDto) => void;
}

export function NovelTableRow({ novel, hasRunningTasks, stats, expanded, onToggleExpand, onDelete }: NovelTableRowProps) {
  const queryClient = useQueryClient();
  const phase = novelKnowledgePhase(novel.status);
  const badge = phaseBadge(phase);
  const versionCount = stats ? stats.versions.length : 0;
  const sceneCount = stats?.sceneCount;
  const vectorCount = stats?.vectorCount;

  const { data: splitProfiles, isLoading, isError } = useQuery({
    queryKey: ['splitProfiles', novel.novelId],
    queryFn: () => knowledgeApi.listSplitProfilesByNovelId(novel.novelId),
    enabled: expanded,
  });

  const deleteVersionMutation = useMutation({
    mutationFn: (p: SceneSplitProfileDto) => {
      if (p.chunkSize == null || p.chunkOverlap == null) {
        return Promise.reject(new Error("legacy_missing_chunk"));
      }
      return knowledgeApi.deleteVersionByNovelId(novel.novelId, p.version, p.chunkSize, p.chunkOverlap, false);
    },
    onSuccess: (_, p) => {
      toast.success(`数据集 "${splitProfileLabel(p)}" 已删除`);
      queryClient.invalidateQueries({ queryKey: ['splitProfiles', novel.novelId] });
      queryClient.invalidateQueries({ queryKey: ['novelStats'] });
    },
    onError: (error: any) => {
      if (error?.message === 'legacy_missing_chunk') {
        toast.error('该版本缺少滑窗元数据，无法按分区删除；请重新切分入库或联系管理员处理旧数据。');
        return;
      }
      if (handleConflict409(error, '当前小说存在运行中任务，请等待任务完成后再删除版本')) {
        queryClient.invalidateQueries({ queryKey: ['tasks'] });
        return;
      }
      toast.error(`删除数据集失败: ${getApiErrorMessage(error, '删除失败')}`);
    },
  });

  const updatedText = novel.updatedAt ? format(new Date(novel.updatedAt), 'yyyy-MM-dd HH:mm') : '—';

  return (
    <>
      <tr className="transition-colors hover:bg-slate-50/80">
        <td className="px-4 py-3">
          <button
            type="button"
            onClick={onToggleExpand}
            aria-expanded={expanded}
            aria-controls={`novel-expand-${novel.novelId}`}
            className="flex items-center gap-2 min-w-0 w-full text-left"
          >
            {expanded ? <ChevronDown className="w-4 h-4 text-slate-400 shrink-0" /> : <ChevronRight className="w-4 h-4 text-slate-400 shrink-0" />}
            <span className="min-w-0">
              <span className="block font-medium text-slate-800 truncate">{novel.title || novel.novelId}</span>
              <span className="block text-xs font-mono text-slate-400 truncate">{novel.novelId}</span>
            </span>
          </button>
        </td>
        <td className="px-4 py-3">
          <span className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[10px] font-semibold ${badge.className}`}>{badge.label}</span>
          <div className="mt-1 text-xs text-slate-500">{versionCount > 0 ? `${versionCount} 个版本` : '无版本'}</div>
        </td>
        <td className="px-4 py-3 text-xs text-slate-600 tabular-nums">
          {sceneCount != null ? sceneCount.toLocaleString() : '—'}
          {' / '}
          {vectorCount != null ? vectorCount.toLocaleString() : '—'}
        </td>
        <td className="px-4 py-3 text-xs text-slate-500">{updatedText}</td>
        <td className="px-4 py-3">
          <div className="flex items-center justify-end gap-2">
            <Link
              to={`/process?novelId=${encodeURIComponent(novel.novelId)}`}
              className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 hover:text-indigo-800 hover:underline"
            >
              <FileInput className="w-3.5 h-3.5" />
              {phase === 'ready' ? '维护' : '去处理'}
            </Link>
            <button
              type="button"
              onClick={() => onDelete(novel)}
              disabled={hasRunningTasks}
              className="inline-flex items-center gap-1 p-1 rounded-md text-slate-300 hover:text-red-500 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="删除知识库"
              title={hasRunningTasks ? "存在运行中任务，暂不可删除" : "删除知识库"}
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </td>
      </tr>
      {expanded && (
        <tr id={`novel-expand-${novel.novelId}`}>
          <td colSpan={5} className="px-4 pb-4 bg-slate-50/40">
            {isLoading ? (
              <div className="flex justify-center py-3"><Loader2 className="w-4 h-4 animate-spin text-slate-300" /></div>
            ) : isError ? (
              <div className="flex items-center gap-2 text-xs text-red-500 bg-red-50 border border-red-100 px-3 py-2 rounded-lg">
                <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                获取版本列表失败
              </div>
            ) : splitProfiles && splitProfiles.length > 0 ? (
              <div className="flex flex-col gap-2">
                {splitProfiles.map((p) => (
                  <VersionTag
                    key={`${p.version}-${p.chunkSize ?? 'x'}-${p.chunkOverlap ?? 'x'}`}
                    version={splitProfileLabel(p)}
                    onDelete={() => deleteVersionMutation.mutate(p)}
                    isPending={deleteVersionMutation.isPending}
                    disabled={hasRunningTasks || p.chunkSize == null || p.chunkOverlap == null}
                    stat={undefined}
                  />
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-400 italic">暂无版本数据</p>
            )}
          </td>
        </tr>
      )}
    </>
  );
}
