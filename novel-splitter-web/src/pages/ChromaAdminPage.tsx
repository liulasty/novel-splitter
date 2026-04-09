import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Server, Database, Activity, Clock, ShieldCheck, Stethoscope, AlertTriangle, DownloadCloud } from "lucide-react";
import { chromaAdminApi } from "@/api/chromaAdminApi";
import { novelApi } from "@/api/novelApi";

import { cn } from "@/lib/utils";
import { toast } from 'sonner';

type TabType = 'system' | 'collections' | 'diagnostics';

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
          onClick={() => setActiveTab('collections')}
          className={cn("px-4 py-2 font-medium text-sm border-b-2", activeTab === 'collections' ? "border-blue-500 text-blue-600" : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")}
        >
          <div className="flex items-center gap-2"><Database className="w-4 h-4" />集合列表</div>
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
        {activeTab === 'collections' && <CollectionsTab />}
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
                <div className="text-sm text-gray-500 mt-2 space-y-1">
                  <p>Database: {col.database}</p>
                  <p>Tenant: {col.tenant}</p>
                  <p className="flex items-center gap-1 mt-1">
                    Space: <span className="font-mono text-xs">{col.metadata?.['hnsw:space'] || 'l2 (未设置)'}</span>
                  </p>
                </div>
                {col.metadata?.['hnsw:space'] !== 'cosine' && (
                    <div className="mt-2 px-2 py-1.5 bg-amber-50 border border-amber-200 rounded text-amber-700 text-xs flex items-center gap-1.5">
                        <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />
                        当前空间类型不为 cosine，可能导致检索距离计算不准确
                    </div>
                )}
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

  const uniqueNovels = Array.from(new Set(stats?.map(s => s.novelName) || []));
  const availableVersions = stats?.find(s => s.novelName === selectedNovel)?.versions || [];

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

  const deleteVersionMutation = useMutation({
    mutationFn: () => chromaAdminApi.deleteVersion(selectedNovel, selectedVersion),
    onSuccess: (data) => {
      toast.success(data?.message || `已成功删除 ${selectedNovel} - ${selectedVersion}`);
    },
    onError: (err: any) => {
      toast.error(`删除失败: ${err.message}`);
    }
  });

  const handleRebuild = () => {
    if (confirmRebuild !== 'REBUILD') {
      toast.error('请输入大写 REBUILD 确认');
      return;
    }
    rebuildMutation.mutate();
  };

  const handleDeleteVersion = () => {
    if (!selectedNovel || !selectedVersion) {
      toast.error('请先选择小说和版本');
      return;
    }
    toast("确定要删除此版本的向量数据吗？", {
      description: "此操作将从 ChromaDB 中永久删除该版本的所有向量。",
      action: {
        label: "确定删除",
        onClick: () => deleteVersionMutation.mutate(),
      },
      cancel: {
        label: "取消",
        onClick: () => {},
      }
    });
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
          <CardDescription className="text-red-600/80">不可逆的数据销毁操作，请谨慎执行。</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center justify-between border-b border-red-100 pb-4">
            <div>
              <p className="font-medium text-gray-900">删除指定版本的向量数据</p>
              <p className="text-sm text-gray-500">仅从 ChromaDB 物理删除当前选中的小说版本向量，不影响本地 JPA 记录。</p>
            </div>
            <button
              onClick={handleDeleteVersion}
              disabled={!selectedNovel || !selectedVersion || deleteVersionMutation.isPending}
              className="bg-red-100 text-red-700 hover:bg-red-200 px-4 py-2 rounded-md text-sm font-medium transition-colors disabled:opacity-50"
            >
              {deleteVersionMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin mx-auto" /> : "删除版本"}
            </button>
          </div>
          <div className="flex items-center justify-between">
            <div>
              <p className="font-medium text-gray-900">重建 Chroma Collection</p>
              <p className="text-sm text-gray-500">销毁当前 Collection -{'>'} 重建并注入 hnsw:space=cosine -{'>'} 清空本地映射表。</p>
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

