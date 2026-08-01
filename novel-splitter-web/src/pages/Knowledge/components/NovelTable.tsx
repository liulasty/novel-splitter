import type { NovelSummaryDto, NovelStatRecordDto } from "@/api/novelApi";
import { NovelTableRow } from "./NovelTableRow";

interface NovelTableProps {
  novels: NovelSummaryDto[];
  runningNovelIds: Set<string>;
  statsMap: Map<string, NovelStatRecordDto>;
  expandedNovelId: string | null;
  onToggleExpand: (novelId: string) => void;
  onDelete: (novel: NovelSummaryDto) => void;
}

export function NovelTable({ novels, runningNovelIds, statsMap, expandedNovelId, onToggleExpand, onDelete }: NovelTableProps) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white overflow-hidden">
      <table className="w-full text-sm" aria-label="知识库列表">
        <thead>
          <tr className="border-b border-slate-200 bg-slate-50/50 text-left text-xs font-semibold text-slate-500 uppercase tracking-wide">
            <th scope="col" className="px-4 py-3">小说</th>
            <th scope="col" className="px-4 py-3 w-28">状态 / 版本</th>
            <th scope="col" className="px-4 py-3 w-32">场景 / 向量</th>
            <th scope="col" className="px-4 py-3 w-40">更新时间</th>
            <th scope="col" className="px-4 py-3 w-32 text-right">操作</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {novels.map((n) => (
            <NovelTableRow
              key={n.novelId}
              novel={n}
              hasRunningTasks={runningNovelIds.has(n.novelId)}
              stats={statsMap.get(n.novelId)}
              expanded={expandedNovelId === n.novelId}
              onToggleExpand={() => onToggleExpand(n.novelId)}
              onDelete={onDelete}
            />
          ))}
          {novels.length === 0 && (
            <tr>
              <td colSpan={5} className="px-4 py-12 text-center text-sm text-slate-400">
                无匹配小说
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
