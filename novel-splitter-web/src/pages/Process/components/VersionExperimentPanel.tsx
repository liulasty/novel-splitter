import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertCircle, FlaskConical, Layers, Loader2, Plus } from 'lucide-react';
import type { CreateVersionRequest } from '@/api/novelApi';
import type { ProcessState, ProcessActions } from './ProcessTypes';
import { VersionRow } from './VersionRow';

interface VersionExperimentPanelProps {
  state: ProcessState;
  actions: ProcessActions;
}

const SPLIT_STRATEGIES: Array<{ value: string; label: string }> = [
  { value: 'SCENE_BOUNDARY', label: '场景边界切分' },
  { value: 'OVERLAP_CHUNK', label: '重叠分块' },
  { value: 'SEMANTIC', label: '语义切分' },
];

/**
 * 版本实验视图：基准就绪后，按「创建版本 → 切分 → 向量化 → 激活」操作版本行。
 */
export function VersionExperimentPanel({ state, actions }: VersionExperimentPanelProps) {
  const { currentNovelId, versions, versionsLoading, isBaselineReady, isCreatingVersion } = state;

  const handleCreate = (body: CreateVersionRequest) => {
    actions.createVersion(body);
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center gap-2 flex-wrap">
        <FlaskConical className="w-4 h-4 text-violet-500" />
        <h3 className="text-sm font-semibold text-slate-700">版本实验</h3>
        <span className="text-[10px] font-semibold uppercase tracking-wider px-2 py-0.5 rounded-full bg-violet-100 text-violet-700">
          create → split → embed → activate
        </span>
      </div>

      {!currentNovelId ? (
        <div className="flex items-center gap-2 px-4 py-3 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-500">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          请先从上方选择已解析的小说，再进行版本实验。
        </div>
      ) : !isBaselineReady ? (
        <div className="flex items-center justify-between gap-3 px-4 py-3 rounded-xl bg-amber-50 border border-amber-200 text-sm text-amber-800 flex-wrap">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            当前小说尚未完成章节解析，请先在 <span className="font-medium">/ingest</span> 完成基准解析后再创建版本。
          </div>
          <Link
            to="/ingest"
            className="inline-flex items-center gap-1.5 h-8 px-3 rounded-full text-xs font-medium text-amber-900 bg-amber-100 hover:bg-amber-200 transition-colors"
          >
            前往 /ingest
          </Link>
        </div>
      ) : null}

      {/* 创建版本表单（基准未就绪时禁用） */}
      {currentNovelId ? (
        <VersionCreateForm isPending={isCreatingVersion} disabled={!isBaselineReady} onCreate={handleCreate} />
      ) : null}

      {/* 版本列表 */}
      <div className="space-y-3">
        {versionsLoading ? (
          <div className="flex justify-center py-10">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : versions.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-2 px-4 py-10 rounded-xl border border-dashed border-slate-200 bg-white/60 text-center">
            <Layers className="w-6 h-6 text-slate-300" />
            <p className="text-sm text-slate-500">暂无版本，创建第一个版本开始切分实验。</p>
          </div>
        ) : (
          versions.map((v) => (
            <VersionRow
              key={v.versionTag}
              version={v}
              isStartingSplit={state.isStartingSplit}
              isStartingEmbed={state.isStartingEmbed}
              isActivating={state.isActivating}
              isDeletingVersion={state.isDeletingVersion}
              onStartSplit={() => actions.startSplit(v.versionTag)}
              onStartEmbed={() => actions.startEmbed(v.versionTag)}
              onActivate={() => actions.activate(v.versionTag)}
              onDelete={() => actions.deleteVersion(v.versionTag)}
              onReEnrich={() => actions.reEnrich(v.versionTag)}
              onResetEnrich={() => actions.resetVersionEnrich(v.versionTag)}
            />
          ))
        )}
      </div>
    </div>
  );
}

interface VersionCreateFormProps {
  isPending: boolean;
  disabled: boolean;
  onCreate: (body: CreateVersionRequest) => void;
}

function VersionCreateForm({ isPending, disabled, onCreate }: VersionCreateFormProps) {
  const [versionTag, setVersionTag] = useState('');
  const [splitStrategy, setSplitStrategy] = useState('OVERLAP_CHUNK');
  const [chunkSize, setChunkSize] = useState(350);
  const [chunkOverlap, setChunkOverlap] = useState(65);

  const handleSubmit = () => {
    onCreate({
      ...(versionTag.trim() !== '' ? { versionTag: versionTag.trim() } : {}),
      splitStrategy,
      chunkSize,
      chunkOverlap,
    });
  };

  return (
    <div className="rounded-xl border border-slate-200 bg-white/80 p-4 space-y-3">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">版本标识（可选）</label>
          <input
            type="text"
            value={versionTag}
            onChange={(e) => setVersionTag(e.target.value)}
            placeholder="留空自动生成 v2/v3…"
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm font-mono text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">切分策略</label>
          <select
            value={splitStrategy}
            onChange={(e) => setSplitStrategy(e.target.value)}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          >
            {SPLIT_STRATEGIES.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </select>
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">场景块大小（字）</label>
          <input
            type="number"
            value={chunkSize}
            onChange={(e) => setChunkSize(Number(e.target.value))}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">重叠（字）</label>
          <input
            type="number"
            value={chunkOverlap}
            onChange={(e) => setChunkOverlap(Number(e.target.value))}
            className="w-full h-9 rounded-lg border border-gray-200 bg-white px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-indigo-400"
          />
        </div>
      </div>

      <div className="flex justify-end">
        <button
          type="button"
          onClick={handleSubmit}
          disabled={disabled || isPending}
          title={disabled ? '请先在 /ingest 完成章节解析' : undefined}
          className="inline-flex items-center gap-2 h-9 px-5 rounded-full text-sm font-medium text-white bg-gradient-to-r from-violet-500 to-indigo-600 hover:from-violet-600 hover:to-indigo-700 hover:shadow-lg transition-all disabled:opacity-40 disabled:pointer-events-none"
        >
          {isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
          创建版本
        </button>
      </div>
    </div>
  );
}
