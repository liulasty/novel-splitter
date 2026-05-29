import { useMemo, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { settingsApi, type ConfigItem } from '@/api/settingsApi';
import { Loader2, Save, Trash2, Plus, X, RefreshCw, Eye, EyeOff, Check, Zap, BrainCircuit } from 'lucide-react';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';

const MASKED = '••••••••';

const PROVIDERS = [
  { key: 'deepseek', label: 'DeepSeek', match: (k: string) => k.startsWith('llm.deepseek.') },
  { key: 'gemini', label: 'Gemini', match: (k: string) => k.startsWith('llm.gemini.') },
  { key: 'coze', label: 'Coze', match: (k: string) => k.startsWith('llm.coze.') },
  { key: 'ollama', label: 'Ollama', match: (k: string) => k.startsWith('llm.ollama.') },
];

type SubGroup = { label: string; match: (k: string) => boolean };
const SUB_GROUPS: Record<string, SubGroup[]> = {
  splitter: [
    { label: 'Ingestion', match: (k: string) => k.startsWith('splitter.ingestion.') },
    { label: 'Rule', match: (k: string) => k.startsWith('splitter.rule.') },
    { label: 'Embed', match: (k: string) => k.startsWith('splitter.embed.') },
    { label: 'Downloader', match: (k: string) => k.startsWith('splitter.downloader.') },
    { label: 'RabbitMQ', match: (k: string) => k.startsWith('splitter.rabbitmq.') },
    { label: 'Storage', match: (k: string) => k.startsWith('splitter.storage.') },
  ],
  embedding_chroma: [
    { label: 'Embedding', match: (k: string) => k.startsWith('embedding.') },
    { label: 'ChromaDB', match: (k: string) => k.startsWith('chroma.') },
  ],
  rag: [],
  assembler: [],
};

const CATEGORY_LABELS: Record<string, string> = {
  llm: 'LLM 服务', splitter: '切分策略', embedding_chroma: 'Embedding & Chroma',
  rag: 'RAG 参数', assembler: '上下文组装',
};

function isSecretKey(k: string) {
  const l = k.toLowerCase();
  return l.includes('api-key') || l.includes('api_key') || l.includes('apikey')
    || l.includes('password') || l.includes('passwd') || l.includes('secret') || l.includes('token');
}

/** Group config keys into logical sections for display */
function groupKeys(items: ConfigItem[]): { name: string; items: ConfigItem[] }[] {
  const groups: Record<string, ConfigItem[]> = {};
  for (const item of items) {
    const parts = item.configKey.split('.');
    let group: string;
    if (parts.length >= 3) {
      // e.g. llm.deepseek.rate-limit.max-requests → group = "rate-limit"
      group = parts[parts.length - 2] === 'options' ? parts[parts.length - 1] : parts[parts.length - 2];
    } else {
      group = 'general';
    }
    (groups[group] ??= []).push(item);
  }
  return Object.entries(groups).map(([name, items]) => ({ name, items }));
}

// ---- Shared row component ----
function ConfigRow({ item, editing, onEdit, onSave, onDelete, onToggleSecret, showSecret }: {
  item: ConfigItem;
  editing: string;
  onEdit: (key: string, field: string, val: string) => void;
  onSave: (item: ConfigItem) => void;
  onDelete: (id: number) => void;
  onToggleSecret: (key: string) => void;
  showSecret: boolean;
}) {
  const secret = isSecretKey(item.configKey);
  const masked = item.configValue === MASKED;
  const dirty = editing !== item.configValue;

  return (
    <div className={cn(
      'px-5 py-2.5 flex items-center gap-4 group transition-colors',
      !item.isDefault && 'bg-amber-50/40'
    )}>
      <div className="flex-1 min-w-0">
        <code className="text-xs font-mono text-gray-600">{item.configKey}</code>
        {item.description && (
          <div className="text-[11px] text-gray-400 mt-0.5">{item.description}</div>
        )}
      </div>
      <div className="relative w-56 shrink-0">
        <input
          type={secret && !showSecret ? 'password' : 'text'}
          className={cn(
            'w-full px-2.5 py-1.5 text-sm border rounded-lg bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/25 focus:border-blue-400 font-mono transition-colors',
            masked && !dirty ? 'text-gray-400 border-gray-200' : 'border-gray-200'
          )}
          value={editing}
          onChange={e => onEdit(item.configKey, 'value', e.target.value)}
        />
        {secret && (
          <button type="button" onClick={() => onToggleSecret(item.configKey)}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
            {showSecret ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
          </button>
        )}
      </div>
      <span className={cn(
        'shrink-0 text-[10px] font-semibold px-1.5 py-0.5 rounded',
        item.isDefault ? 'bg-gray-100 text-gray-500' : 'bg-amber-100 text-amber-700'
      )}>
        {item.isDefault ? 'yml' : 'DB'}
      </span>
      <div className="w-14 shrink-0 flex justify-end opacity-0 group-hover:opacity-100 transition-opacity">
        {dirty && (
          <button onClick={() => onSave(item)} className="p-1 text-green-600 hover:bg-green-50 rounded" title="保存">
            <Save className="w-3.5 h-3.5" />
          </button>
        )}
        {!item.isDefault && item.id != null && (
          <button onClick={() => onDelete(item.id!)} className="p-1 text-red-400 hover:bg-red-50 rounded" title="删除">
            <Trash2 className="w-3.5 h-3.5" />
          </button>
        )}
      </div>
    </div>
  );
}

// ---- Main ----
export default function SettingsPage() {
  const queryClient = useQueryClient();
  const [cat, setCat] = useState('llm');
  // llm specific: which provider's config tab is active
  const [activeProvider, setActiveProvider] = useState(0);
  // non-llm: which sub-group tab
  const [subIdx, setSubIdx] = useState(0);
  const [editing, setEditing] = useState<Record<string, string>>({});
  const [addForm, setAddForm] = useState<{ key: string; value: string; description: string } | null>(null);
  const [showSecrets, setShowSecrets] = useState<Record<string, boolean>>({});

  const { data, isLoading } = useQuery({ queryKey: ['systemSettings'], queryFn: settingsApi.getSettings });

  const saveMut = useMutation({
    mutationFn: settingsApi.saveConfig,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['systemSettings'] }); toast.success('配置已保存'); },
    onError: (e: Error) => toast.error('保存失败: ' + e.message),
  });
  const delMut = useMutation({
    mutationFn: (id: number) => settingsApi.deleteConfig(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['systemSettings'] }); toast.success('已删除'); },
    onError: (e: Error) => toast.error('删除失败: ' + e.message),
  });

  const categories = data?.categories || {};
  const allItems = categories[cat] || [];

  // --- LLM-specific data ---
  const providerKey = 'novel.llm.provider';
  const providerItem = allItems.find(i => i.configKey === providerKey);
  const currentProvider = (providerItem?.configValue || 'deepseek').toLowerCase();
  const currentProviderIdx = PROVIDERS.findIndex(p => p.key === currentProvider);

  const providerItems = useMemo(() => {
    const match = PROVIDERS[activeProvider]?.match;
    return match ? allItems.filter(i => match(i.configKey)) : [];
  }, [allItems, activeProvider]);

  // --- Non-LLM sub-group ---
  const subGroups = SUB_GROUPS[cat] || [];
  const subItems = useMemo(() => {
    if (!subGroups.length) return allItems;
    const match = subGroups[subIdx]?.match;
    return match ? allItems.filter(i => match(i.configKey)) : allItems;
  }, [allItems, subGroups, subIdx]);

  // --- shared ---
  const getEditVal = (item: ConfigItem) => editing[item.configKey] ?? item.configValue;
  const handleEdit = (key: string, _field: string, val: string) => setEditing(p => ({ ...p, [key]: val }));
  const handleSave = (item: ConfigItem) => {
    saveMut.mutate({
      configKey: item.configKey, configValue: getEditVal(item),
      category: item.category || cat, description: item.description,
    });
    setEditing(p => { const n = { ...p }; delete n[item.configKey]; return n; });
  };
  const handleAdd = () => {
    if (!addForm?.key.trim()) return;
    saveMut.mutate({ configKey: addForm.key.trim(), configValue: addForm.value, category: cat, description: addForm.description }, { onSuccess: () => setAddForm(null) });
  };

  const switchProvider = (idx: number) => {
    setActiveProvider(idx);
    const provKey = PROVIDERS[idx].key;
    saveMut.mutate({ configKey: providerKey, configValue: provKey, category: 'llm', description: '当前激活的 LLM 厂商' });
  };

  if (isLoading) return <div className="flex items-center justify-center h-64"><Loader2 className="w-8 h-8 animate-spin text-blue-500" /></div>;

  return (
    <div className="max-w-5xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-gray-900">系统配置</h1>
          <p className="text-gray-500 text-sm mt-0.5">保存写入数据库覆盖 yml 默认值，删除则回退默认</p>
        </div>
        <button onClick={() => queryClient.invalidateQueries({ queryKey: ['systemSettings'] })}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-600 bg-white border rounded-lg hover:bg-gray-50">
          <RefreshCw className="w-4 h-4" /> 刷新
        </button>
      </div>

      <div className="flex gap-6">
        {/* Left nav */}
        <div className="w-44 shrink-0 flex flex-col gap-1">
          {Object.entries(CATEGORY_LABELS).map(([id, label]) => {
            const count = categories[id]?.length || 0;
            return (
              <button key={id} onClick={() => { setCat(id); setSubIdx(0); setAddForm(null); }}
                className={cn(
                  'w-full flex items-center justify-between px-3.5 py-2 rounded-lg text-sm font-medium transition-colors text-left',
                  cat === id ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-100'
                )}>
                <span>{label}</span>
                {count > 0 && <span className="text-xs text-gray-400 font-mono">{count}</span>}
              </button>
            );
          })}
        </div>

        {/* Right content */}
        <div className="flex-1 min-w-0 space-y-4">
          {/* ============ LLM section ============ */}
          {cat === 'llm' && (
            <>
              {/* Provider selector card */}
              <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                <div className="px-5 py-3 bg-gray-50 border-b border-gray-200 flex items-center gap-2">
                  <Zap className="w-4 h-4 text-amber-500" />
                  <span className="text-sm font-semibold text-gray-700">当前激活</span>
                  <span className="text-xs text-gray-400">选择回答对话时使用的大语言模型</span>
                </div>
                <div className="p-4 grid grid-cols-4 gap-3">
                  {PROVIDERS.map((prov, i) => {
                    const actuallyActive = currentProviderIdx >= 0 ? currentProviderIdx === i : i === 0;
                    return (
                      <button key={prov.key} onClick={() => switchProvider(i)}
                        className={cn(
                          'relative p-4 rounded-xl border-2 text-left transition-all',
                          actuallyActive
                            ? 'border-blue-400 bg-blue-50/50 shadow-sm'
                            : 'border-gray-150 bg-gray-50/50 hover:border-gray-300'
                        )}>
                        <div className="flex items-center gap-2">
                          <BrainCircuit className={cn('w-4 h-4', actuallyActive ? 'text-blue-600' : 'text-gray-400')} />
                          <span className={cn('text-sm font-semibold', actuallyActive ? 'text-blue-800' : 'text-gray-700')}>
                            {prov.label}
                          </span>
                        </div>
                        {actuallyActive && <Check className="absolute top-3 right-3 w-4 h-4 text-blue-500" />}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Provider config sub-tabs */}
              <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
                <div className="flex gap-1 px-2 py-2 bg-gray-50 border-b border-gray-200">
                  {PROVIDERS.map((prov, i) => (
                    <button key={prov.key} onClick={() => setActiveProvider(i)}
                      className={cn(
                        'px-3.5 py-1.5 text-xs font-medium rounded-md transition-colors',
                        activeProvider === i ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700'
                      )}>
                      {prov.label}
                    </button>
                  ))}
                  <div className="flex-1" />
                  <button onClick={() => setAddForm({ key: '', value: '', description: '' })}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-blue-600 hover:text-blue-800">
                    <Plus className="w-3 h-3" /> 新增参数
                  </button>
                </div>

                {addForm && (
                  <div className="px-5 py-3 bg-blue-50/40 border-b border-blue-100 flex gap-3 items-center">
                    <input className="flex-1 px-2.5 py-1.5 text-sm border rounded-lg" placeholder="config key"
                      value={addForm.key} onChange={e => setAddForm({ ...addForm, key: e.target.value })} />
                    <input className="w-36 px-2.5 py-1.5 text-sm border rounded-lg" placeholder="值"
                      value={addForm.value} onChange={e => setAddForm({ ...addForm, value: e.target.value })} />
                    <input className="w-32 px-2.5 py-1.5 text-sm border rounded-lg" placeholder="说明"
                      value={addForm.description} onChange={e => setAddForm({ ...addForm, description: e.target.value })} />
                    <button onClick={handleAdd} className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700">保存</button>
                    <button onClick={() => setAddForm(null)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                  </div>
                )}

                {/* Grouped config */}
                {providerItems.length === 0 ? (
                  <div className="px-5 py-12 text-center text-sm text-gray-400">暂无配置</div>
                ) : (
                  groupKeys(providerItems).map(group => (
                    <div key={group.name}>
                      <div className="px-5 py-1.5 bg-gray-50/50 border-b border-gray-100">
                        <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">{group.name}</span>
                      </div>
                      {group.items.map(item => (
                        <ConfigRow key={item.configKey} item={item}
                          editing={getEditVal(item)}
                          onEdit={handleEdit}
                          onSave={handleSave}
                          onDelete={(id) => delMut.mutate(id)}
                          onToggleSecret={(k) => setShowSecrets(p => ({ ...p, [k]: !p[k] }))}
                          showSecret={!!showSecrets[item.configKey]}
                        />
                      ))}
                    </div>
                  ))
                )}
              </div>
            </>
          )}

          {/* ============ Non-LLM sections ============ */}
          {cat !== 'llm' && (
            <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
              {/* Sub-tabs */}
              {subGroups.length > 0 && (
                <div className="flex gap-1 px-2 py-2 bg-gray-50 border-b border-gray-200">
                  {subGroups.map((sg, i) => (
                    <button key={sg.label} onClick={() => setSubIdx(i)}
                      className={cn('px-3.5 py-1.5 text-xs font-medium rounded-md transition-colors',
                        subIdx === i ? 'bg-white text-gray-800 shadow-sm' : 'text-gray-500 hover:text-gray-700')}>
                      {sg.label}
                    </button>
                  ))}
                  <div className="flex-1" />
                  <button onClick={() => setAddForm({ key: '', value: '', description: '' })}
                    className="flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-blue-600 hover:text-blue-800">
                    <Plus className="w-3 h-3" /> 新增参数
                  </button>
                </div>
              )}

              {addForm && (
                <div className="px-5 py-3 bg-blue-50/40 border-b border-blue-100 flex gap-3 items-center">
                  <input className="flex-1 px-2.5 py-1.5 text-sm border rounded-lg" placeholder="config key"
                    value={addForm.key} onChange={e => setAddForm({ ...addForm, key: e.target.value })} />
                  <input className="w-36 px-2.5 py-1.5 text-sm border rounded-lg" placeholder="值"
                    value={addForm.value} onChange={e => setAddForm({ ...addForm, value: e.target.value })} />
                  <input className="w-32 px-2.5 py-1.5 text-sm border rounded-lg" placeholder="说明"
                    value={addForm.description} onChange={e => setAddForm({ ...addForm, description: e.target.value })} />
                  <button onClick={handleAdd} className="px-3 py-1.5 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700">保存</button>
                  <button onClick={() => setAddForm(null)} className="text-gray-400 hover:text-gray-600"><X className="w-4 h-4" /></button>
                </div>
              )}

              {subItems.length === 0 ? (
                <div className="px-5 py-12 text-center text-sm text-gray-400">暂无配置</div>
              ) : (
                groupKeys(subItems).map(group => (
                  <div key={group.name}>
                    <div className="px-5 py-1.5 bg-gray-50/50 border-b border-gray-100">
                      <span className="text-[11px] font-semibold text-gray-500 uppercase tracking-wider">{group.name}</span>
                    </div>
                    {group.items.map(item => (
                      <ConfigRow key={item.configKey} item={item}
                        editing={getEditVal(item)}
                        onEdit={handleEdit}
                        onSave={handleSave}
                        onDelete={(id) => delMut.mutate(id)}
                        onToggleSecret={(k) => setShowSecrets(p => ({ ...p, [k]: !p[k] }))}
                        showSecret={!!showSecrets[item.configKey]}
                      />
                    ))}
                  </div>
                ))
              )}
            </div>
          )}

          {/* Legend */}
          <div className="flex gap-4 text-xs text-gray-400">
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-gray-300" /> yml 默认</span>
            <span className="flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-amber-300" /> 数据库覆盖</span>
            <span className="flex items-center gap-1">{MASKED} 密钥已掩码</span>
          </div>
        </div>
      </div>
    </div>
  );
}
