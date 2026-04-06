import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Server, Database, Activity, Clock, ShieldCheck, ListTree, DatabaseZap, Search, Stethoscope, AlertTriangle, DownloadCloud } from "lucide-react";
import { chromaAdminApi } from "@/api/chromaAdminApi";
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";
import { cn } from "@/lib/utils";
import { toast } from 'sonner';

type TabType = 'system' | 'tenants' | 'collections' | 'records' | 'diagnostics';

export default function ChromaAdminPage() {
  const [activeTab, setActiveTab] = useState<TabType>('system');

  return (
    <div className="max-w-6xl mx-auto space-y-8">
      <div className="flex flex-col gap-2">
        <h1 className="text-3xl font-bold tracking-tight text-gray-900">ChromaDB 管理后台</h1>
        <p className="text-gray-500">
          直接管理和监控底层的 Chroma 向量数据库实例。
        </p>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-200">
        <button
          onClick={() => setActiveTab('system')}
          className={cn("px-4 py-2 font-medium text-sm border-b-2", activeTab === 'system' ? "border-blue-500 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")}
        >
          <div className="flex items-center gap-2"><Server className="w-4 h-4" />系统状态</div>
        </button>
        <button
          onClick={() => setActiveTab('tenants')}
          className={cn("px-4 py-2 font-medium text-sm border-b-2", activeTab === 'tenants' ? "border-blue-500 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")}
        >
          <div className="flex items-center gap-2"><ListTree className="w-4 h-4" />租户与数据库</div>
        </button>
        <button
          onClick={() => setActiveTab('collections')}
          className={cn("px-4 py-2 font-medium text-sm border-b-2", activeTab === 'collections' ? "border-blue-500 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")}
        >
          <div className="flex items-center gap-2"><Database className="w-4 h-4" />集合列表</div>
        </button>
        <button
          onClick={() => setActiveTab('records')}
          className={cn("px-4 py-2 font-medium text-sm border-b-2", activeTab === 'records' ? "border-blue-500 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")}
        >
          <div className="flex items-center gap-2"><Search className="w-4 h-4" />记录检索</div>
        </button>
        <button
          onClick={() => setActiveTab('diagnostics')}
          className={cn("px-4 py-2 font-medium text-sm border-b-2", activeTab === 'diagnostics' ? "border-blue-500 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")}
        >
          <div className="flex items-center gap-2"><Stethoscope className="w-4 h-4" />诊断与高级操作</div>
        </button>
      </div>

      <div className="mt-6">
        {activeTab === 'system' && <SystemTab />}
        {activeTab === 'tenants' && <TenantsTab />}
        {activeTab === 'collections' && <CollectionsTab />}
        {activeTab === 'records' && <RecordsTab />}
        {activeTab === 'diagnostics' && <DiagnosticsTab />}
      </div>
    </div>
  );
}

// --- System Tab ---
function SystemTab() {
  const { data: health, isLoading: hLoading } = useQuery({ queryKey: ['chroma-health'], queryFn: chromaAdminApi.getHealthcheck });
  const { data: version, isLoading: vLoading } = useQuery({ queryKey: ['chroma-version'], queryFn: chromaAdminApi.getVersion });
  const { data: heartbeat, isLoading: hbLoading } = useQuery({ queryKey: ['chroma-heartbeat'], queryFn: chromaAdminApi.getHeartbeat, refetchInterval: 10000 });
  const { data: preflight, isLoading: pfLoading } = useQuery({ queryKey: ['chroma-preflight'], queryFn: chromaAdminApi.getPreFlightChecks });
  const { data: auth, isLoading: aLoading } = useQuery({ queryKey: ['chroma-auth'], queryFn: chromaAdminApi.getAuthIdentity });

  return (
    <div className="grid gap-6 md:grid-cols-3">
      <Card>
        <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2"><Activity className="w-4 h-4" />健康状态</CardTitle></CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{hLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : (health?.['nanosecond heartbeat'] ? <span className="text-green-600">在线</span> : <span className="text-red-600">离线</span>)}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2"><Server className="w-4 h-4" />版本信息</CardTitle></CardHeader>
        <CardContent>
          <div className="text-2xl font-bold">{vLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : version?.version || "未知"}</div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2"><Clock className="w-4 h-4" />心跳</CardTitle></CardHeader>
        <CardContent>
          <div className="text-xl font-bold truncate">{hbLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : heartbeat?.['nanosecond heartbeat'] || "--"}</div>
        </CardContent>
      </Card>
      <Card className="md:col-span-3">
        <CardHeader className="pb-2"><CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2"><ShieldCheck className="w-4 h-4" />预检与认证</CardTitle></CardHeader>
        <CardContent className="grid md:grid-cols-2 gap-4">
          <div>
            <h4 className="text-sm font-semibold mb-2">Pre-flight Checks</h4>
            <pre className="bg-gray-50 p-3 rounded text-xs overflow-auto h-32">{pfLoading ? 'Loading...' : JSON.stringify(preflight, null, 2)}</pre>
          </div>
          <div>
            <h4 className="text-sm font-semibold mb-2">Auth Identity</h4>
            <pre className="bg-gray-50 p-3 rounded text-xs overflow-auto h-32">{aLoading ? 'Loading...' : JSON.stringify(auth, null, 2)}</pre>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

// --- Tenants & Databases Tab ---
function TenantsTab() {
  const [selectedTenant, setSelectedTenant] = useState<string>('default_tenant');
  const { data: tenants, isLoading: tLoading } = useQuery({ queryKey: ['chroma-tenants'], queryFn: chromaAdminApi.getTenants });
  const { data: databases, isLoading: dLoading } = useQuery({ queryKey: ['chroma-databases', selectedTenant], queryFn: () => chromaAdminApi.getDatabases(selectedTenant), enabled: !!selectedTenant });

  return (
    <div className="grid md:grid-cols-2 gap-6">
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><ListTree className="w-5 h-5" />租户列表 (Tenants)</CardTitle></CardHeader>
        <CardContent>
          {tLoading ? <Loader2 className="w-6 h-6 animate-spin mx-auto" /> : (
            <div className="space-y-2">
              {tenants?.map(t => (
                <button
                  key={t.name}
                  onClick={() => setSelectedTenant(t.name)}
                  className={cn("w-full text-left px-4 py-3 rounded-md border flex items-center justify-between", selectedTenant === t.name ? "border-blue-500 bg-blue-50" : "border-gray-200 hover:bg-gray-50")}
                >
                  <span className="font-medium">{t.name}</span>
                </button>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle className="flex items-center gap-2"><DatabaseZap className="w-5 h-5" />数据库列表 (Databases)</CardTitle><CardDescription>租户: {selectedTenant}</CardDescription></CardHeader>
        <CardContent>
          {dLoading ? <Loader2 className="w-6 h-6 animate-spin mx-auto" /> : (
            <div className="space-y-2">
              {databases?.length === 0 ? <p className="text-gray-500 text-sm">暂无数据库</p> : databases?.map(db => (
                <div key={db.name} className="px-4 py-3 rounded-md border border-gray-200 bg-gray-50">
                  <div className="font-medium">{db.name}</div>
                  <div className="text-xs text-gray-500 mt-1">Tenant: {db.tenant}</div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

// --- Collections Tab ---
function CollectionsTab() {
  const { data: collections, isLoading } = useQuery({ queryKey: ['chroma-all-collections'], queryFn: chromaAdminApi.getCollections });

  return (
    <Card>
      <CardHeader><CardTitle className="flex items-center gap-2"><Database className="w-5 h-5" />集合列表</CardTitle></CardHeader>
      <CardContent>
        {isLoading ? <Loader2 className="w-6 h-6 animate-spin mx-auto" /> : (
          <div className="grid gap-4 md:grid-cols-2">
            {collections?.length === 0 ? <p className="text-gray-500 text-sm">暂无集合</p> : collections?.map(col => (
              <div key={col.id} className="p-4 rounded-lg border border-gray-200 bg-white shadow-sm flex flex-col gap-2">
                <div className="flex justify-between items-start">
                  <h4 className="font-medium text-gray-900">{col.name}</h4>
                  <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded-full truncate max-w-[120px]" title={col.id}>ID: {col.id.substring(0, 8)}...</span>
                </div>
                <div className="text-sm text-gray-500 mt-2">
                  <p>Database: {col.database}</p>
                  <p>Tenant: {col.tenant}</p>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// --- Diagnostics Tab ---
function DiagnosticsTab() {
  const [selectedNovel, setSelectedNovel] = useState('');
  const [selectedVersion, setSelectedVersion] = useState('');
  const [confirmRebuild, setConfirmRebuild] = useState('');
  const [showRebuildModal, setShowRebuildModal] = useState(false);

  const { data: stats } = useQuery({ queryKey: ['novelStats'], queryFn: novelApi.getNovelStats });

  const uniqueNovels = Array.from(new Set(stats?.map(s => s.novelId) || []));
  const availableVersions = stats?.filter(s => s.novelId === selectedNovel).map(s => s.version) || [];

  const { data: diagnostic, isLoading: diagLoading, refetch: runDiag } = useQuery({
    queryKey: ['chroma-diag', selectedNovel, selectedVersion],
    queryFn: () => chromaAdminApi.getDiagnostics(selectedNovel, selectedVersion),
    enabled: false,
  });

  const handleDiagnostic = () => {
    if (!selectedNovel || !selectedVersion) {
      toast.error('请先选择小说和版本');
      return;
    }
    runDiag();
  };

  const rebuildMutation = useMutation({
    mutationFn: chromaAdminApi.rebuildCollection,
    onSuccess: (data) => {
      toast.success(data?.message || "Collection 重建并同步成功");
      setShowRebuildModal(false);
      setConfirmRebuild('');
    },
    onError: (err: any) => {
      toast.error(`重建失败: ${err.message}`);
    }
  });

  const handleRebuild = () => {
    if (confirmRebuild !== 'REBUILD') {
      toast.error('请输入大写 REBUILD 确认');
      return;
    }
    rebuildMutation.mutate();
  };

  const handleExport = () => {
    window.open(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/admin/chroma/export`, '_blank');
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Stethoscope className="w-5 h-5" />数据诊断与一致性</CardTitle>
          <CardDescription>校验本地数据库 (JPA) 与 ChromaDB 之间的记录数及元数据完整性。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-4 items-end">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">小说</label>
              <select className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" value={selectedNovel} onChange={e => { setSelectedNovel(e.target.value); setSelectedVersion(''); }}>
                <option value="">-- 选择小说 --</option>
                {uniqueNovels.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            </div>
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">版本</label>
              <select className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" value={selectedVersion} onChange={e => setSelectedVersion(e.target.value)} disabled={!selectedNovel}>
                <option value="">-- 选择版本 --</option>
                {availableVersions.map(v => <option key={v} value={v}>{v}</option>)}
              </select>
            </div>
            <button onClick={handleDiagnostic} disabled={!selectedNovel || !selectedVersion || diagLoading} className="bg-blue-600 text-white px-6 py-2 rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
              {diagLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : "开始诊断"}
            </button>
          </div>

          {diagnostic && (
            <div className="mt-6 p-4 rounded-lg border border-gray-200 bg-gray-50">
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <p className="text-sm text-gray-500 mb-1">本地数据库记录数</p>
                  <p className="text-2xl font-bold">{diagnostic.localDbCount}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">ChromaDB 记录数</p>
                  <p className="text-2xl font-bold">{diagnostic.chromaCount}</p>
                </div>
              </div>
              <div className="mt-4 pt-4 border-t border-gray-200 flex items-center gap-4">
                <span className={cn("px-3 py-1 rounded-full text-xs font-medium", diagnostic.isConsistent ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700")}>
                  {diagnostic.isConsistent ? "✓ 数量一致" : "✗ 数量不一致"}
                </span>
                <span className={cn("px-3 py-1 rounded-full text-xs font-medium", diagnostic.sampleDataValid ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700")}>
                  {diagnostic.sampleDataValid ? "✓ 元数据完整" : "✗ 元数据异常"}
                </span>
              </div>
              {diagnostic.message && <p className="mt-3 text-sm text-gray-600">{diagnostic.message}</p>}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><DownloadCloud className="w-5 h-5" />大规模元数据导出</CardTitle>
          <CardDescription>通过流式传输 (Streaming) 导出海量向量元数据，防止 OOM。</CardDescription>
        </CardHeader>
        <CardContent>
          <button onClick={handleExport} className="flex items-center gap-2 px-4 py-2 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded-md hover:bg-indigo-100 text-sm font-medium transition-colors">
            <DownloadCloud className="w-4 h-4" />
            导出全部向量元数据 (JSONL)
          </button>
        </CardContent>
      </Card>

      <Card className="border-red-100 bg-red-50/30">
        <CardHeader>
          <CardTitle className="text-red-700 flex items-center gap-2"><AlertTriangle className="w-5 h-5" />危险操作区</CardTitle>
          <CardDescription className="text-red-600/80">这些操作不可逆，会销毁并重建底层的 Chroma Collection 及其本地映射，请谨慎执行。</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-gray-900">重建 Chroma Collection</p>
              <p className="text-sm text-gray-500">销毁当前 Collection -> 重建并注入 hnsw:space=cosine -> 清空本地映射表。</p>
            </div>
            <button
              onClick={() => setShowRebuildModal(true)}
              className="bg-red-600 text-white hover:bg-red-700 px-4 py-2 rounded-md text-sm font-medium transition-colors"
            >
              重建 Collection
            </button>
          </div>
        </CardContent>
      </Card>

      {showRebuildModal && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg p-6 max-w-md w-full shadow-xl">
            <h3 className="text-lg font-bold text-red-600 flex items-center gap-2 mb-2"><AlertTriangle className="w-5 h-5" />严重警告</h3>
            <p className="text-sm text-gray-600 mb-4">此操作将清空所有已有向量数据且无法恢复。为防止误操作，请输入 <strong className="text-red-600 select-all">REBUILD</strong> 以确认。</p>
            <input
              type="text"
              value={confirmRebuild}
              onChange={e => setConfirmRebuild(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 mb-4 font-mono text-center tracking-widest focus:border-red-500 focus:ring-1 focus:ring-red-500"
              placeholder="输入 REBUILD"
            />
            <div className="flex justify-end gap-3">
              <button onClick={() => { setShowRebuildModal(false); setConfirmRebuild(''); }} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded">取消</button>
              <button onClick={handleRebuild} disabled={confirmRebuild !== 'REBUILD' || rebuildMutation.isPending} className="px-4 py-2 text-sm text-white bg-red-600 hover:bg-red-700 rounded disabled:opacity-50 flex items-center gap-2">
                {rebuildMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                确认重建
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// --- Records Tab ---
function RecordsTab() {
  const [selectedCol, setSelectedCol] = useState('');
  const [limit, setLimit] = useState(10);
  const [queryText, setQueryText] = useState('');
  
  const { data: collections } = useQuery({ queryKey: ['chroma-all-collections'], queryFn: chromaAdminApi.getCollections });
  
  const { data: records, isLoading: rLoading, refetch: refetchGet } = useQuery({
    queryKey: ['chroma-records', selectedCol, limit],
    queryFn: () => chromaAdminApi.getRecords(selectedCol, { limit }),
    enabled: !!selectedCol && !queryText,
  });

  const { data: queryResults, isLoading: qLoading, refetch: refetchQuery } = useQuery({
    queryKey: ['chroma-query', selectedCol, queryText, limit],
    queryFn: () => chromaAdminApi.queryRecords(selectedCol, { query_texts: [queryText], n_results: limit }),
    enabled: false,
  });

  const handleQuery = () => {
    if (!selectedCol) {
      toast.error('请先选择集合');
      return;
    }
    if (queryText) {
      refetchQuery();
    } else {
      refetchGet();
    }
  };

  const displayData = queryText ? queryResults : records;
  const isLoading = rLoading || qLoading;

// --- Diagnostics Tab ---
function DiagnosticsTab() {
  const [selectedNovel, setSelectedNovel] = useState('');
  const [selectedVersion, setSelectedVersion] = useState('');
  const [confirmRebuild, setConfirmRebuild] = useState('');
  const [showRebuildModal, setShowRebuildModal] = useState(false);

  const { data: stats } = useQuery({ queryKey: ['novelStats'], queryFn: novelApi.getNovelStats });

  const uniqueNovels = Array.from(new Set(stats?.map(s => s.novelId) || []));
  const availableVersions = stats?.filter(s => s.novelId === selectedNovel).map(s => s.version) || [];

  const { data: diagnostic, isLoading: diagLoading, refetch: runDiag } = useQuery({
    queryKey: ['chroma-diag', selectedNovel, selectedVersion],
    queryFn: () => chromaAdminApi.getDiagnostics(selectedNovel, selectedVersion),
    enabled: false,
  });

  const handleDiagnostic = () => {
    if (!selectedNovel || !selectedVersion) {
      toast.error('请先选择小说和版本');
      return;
    }
    runDiag();
  };

  const rebuildMutation = useMutation({
    mutationFn: chromaAdminApi.rebuildCollection,
    onSuccess: (data) => {
      toast.success(data.message || "Collection 重建并同步成功");
      setShowRebuildModal(false);
      setConfirmRebuild('');
    },
    onError: (err: any) => {
      toast.error(`重建失败: ${err.message}`);
    }
  });

  const handleRebuild = () => {
    if (confirmRebuild !== 'REBUILD') {
      toast.error('请输入大写 REBUILD 确认');
      return;
    }
    rebuildMutation.mutate();
  };

  const handleExport = () => {
    // Navigate to the backend export endpoint
    window.open(`${import.meta.env.VITE_API_URL || 'http://localhost:8080'}/api/admin/chroma/export`, '_blank');
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><Stethoscope className="w-5 h-5" />数据诊断与一致性</CardTitle>
          <CardDescription>校验本地数据库 (JPA) 与 ChromaDB 之间的记录数及元数据完整性。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex gap-4 items-end">
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">小说</label>
              <select className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" value={selectedNovel} onChange={e => { setSelectedNovel(e.target.value); setSelectedVersion(''); }}>
                <option value="">-- 选择小说 --</option>
                {uniqueNovels.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            </div>
            <div className="flex-1">
              <label className="block text-sm font-medium text-gray-700 mb-1">版本</label>
              <select className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" value={selectedVersion} onChange={e => setSelectedVersion(e.target.value)} disabled={!selectedNovel}>
                <option value="">-- 选择版本 --</option>
                {availableVersions.map(v => <option key={v} value={v}>{v}</option>)}
              </select>
            </div>
            <button onClick={handleDiagnostic} disabled={!selectedNovel || !selectedVersion || diagLoading} className="bg-blue-600 text-white px-6 py-2 rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
              {diagLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : "开始诊断"}
            </button>
          </div>

          {diagnostic && (
            <div className="mt-6 p-4 rounded-lg border border-gray-200 bg-gray-50">
              <div className="grid grid-cols-2 gap-6">
                <div>
                  <p className="text-sm text-gray-500 mb-1">本地数据库记录数</p>
                  <p className="text-2xl font-bold">{diagnostic.localDbCount}</p>
                </div>
                <div>
                  <p className="text-sm text-gray-500 mb-1">ChromaDB 记录数</p>
                  <p className="text-2xl font-bold">{diagnostic.chromaCount}</p>
                </div>
              </div>
              <div className="mt-4 pt-4 border-t border-gray-200 flex items-center gap-4">
                <span className={cn("px-3 py-1 rounded-full text-xs font-medium", diagnostic.isConsistent ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700")}>
                  {diagnostic.isConsistent ? "✓ 数量一致" : "✗ 数量不一致"}
                </span>
                <span className={cn("px-3 py-1 rounded-full text-xs font-medium", diagnostic.sampleDataValid ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700")}>
                  {diagnostic.sampleDataValid ? "✓ 元数据完整" : "✗ 元数据异常"}
                </span>
              </div>
              {diagnostic.message && <p className="mt-3 text-sm text-gray-600">{diagnostic.message}</p>}
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2"><DownloadCloud className="w-5 h-5" />大规模元数据导出</CardTitle>
          <CardDescription>通过流式传输 (Streaming) 导出海量向量元数据，防止 OOM。</CardDescription>
        </CardHeader>
        <CardContent>
          <button onClick={handleExport} className="flex items-center gap-2 px-4 py-2 bg-indigo-50 text-indigo-700 border border-indigo-200 rounded-md hover:bg-indigo-100 text-sm font-medium transition-colors">
            <DownloadCloud className="w-4 h-4" />
            导出全部向量元数据 (JSONL)
          </button>
        </CardContent>
      </Card>

      <Card className="border-red-100 bg-red-50/30">
        <CardHeader>
          <CardTitle className="text-red-700 flex items-center gap-2"><AlertTriangle className="w-5 h-5" />危险操作区</CardTitle>
          <CardDescription className="text-red-600/80">这些操作不可逆，会销毁并重建底层的 Chroma Collection 及其本地映射，请谨慎执行。</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-gray-900">重建 Chroma Collection</p>
              <p className="text-sm text-gray-500">销毁当前 Collection -> 重建并注入 hnsw:space=cosine -> 清空本地映射表。</p>
            </div>
            <button
              onClick={() => setShowRebuildModal(true)}
              className="bg-red-600 text-white hover:bg-red-700 px-4 py-2 rounded-md text-sm font-medium transition-colors"
            >
              重建 Collection
            </button>
          </div>
        </CardContent>
      </Card>

      {showRebuildModal && (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4">
          <div className="bg-white rounded-lg p-6 max-w-md w-full shadow-xl">
            <h3 className="text-lg font-bold text-red-600 flex items-center gap-2 mb-2"><AlertTriangle className="w-5 h-5" />严重警告</h3>
            <p className="text-sm text-gray-600 mb-4">此操作将清空所有已有向量数据且无法恢复。为防止误操作，请输入 <strong className="text-red-600 select-all">REBUILD</strong> 以确认。</p>
            <input
              type="text"
              value={confirmRebuild}
              onChange={e => setConfirmRebuild(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2 mb-4 font-mono text-center tracking-widest focus:border-red-500 focus:ring-1 focus:ring-red-500"
              placeholder="输入 REBUILD"
            />
            <div className="flex justify-end gap-3">
              <button onClick={() => { setShowRebuildModal(false); setConfirmRebuild(''); }} className="px-4 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded">取消</button>
              <button onClick={handleRebuild} disabled={confirmRebuild !== 'REBUILD' || rebuildMutation.isPending} className="px-4 py-2 text-sm text-white bg-red-600 hover:bg-red-700 rounded disabled:opacity-50 flex items-center gap-2">
                {rebuildMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
                确认重建
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
        <div className="flex gap-4 items-end flex-wrap">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">集合 (Collection)</label>
            <select className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" value={selectedCol} onChange={e => setSelectedCol(e.target.value)}>
              <option value="">-- 选择集合 --</option>
              {collections?.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </div>
          <div className="w-24">
            <label className="block text-sm font-medium text-gray-700 mb-1">Limit</label>
            <input type="number" className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" value={limit} onChange={e => setLimit(Number(e.target.value))} />
          </div>
          <div className="flex-1 min-w-[200px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">查询文本 (选填, 留空则全量获取)</label>
            <input type="text" className="w-full rounded-md border border-gray-300 px-3 py-2 text-sm" placeholder="输入查询文本进行语义检索..." value={queryText} onChange={e => setQueryText(e.target.value)} onKeyDown={e => e.key === 'Enter' && handleQuery()} />
          </div>
          <button onClick={handleQuery} disabled={!selectedCol || isLoading} className="bg-blue-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50">
            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : "查询"}
          </button>
        </div>

        <div>
          <h4 className="text-sm font-medium text-gray-700 mb-2">结果 (Raw JSON)</h4>
          <div className="bg-gray-900 rounded-lg p-4 overflow-auto max-h-[500px]">
            {isLoading ? <Loader2 className="w-6 h-6 animate-spin text-white mx-auto" /> : (
              <pre className="text-green-400 text-xs">
                {displayData ? JSON.stringify(displayData, null, 2) : "暂无数据，请点击查询"}
              </pre>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
