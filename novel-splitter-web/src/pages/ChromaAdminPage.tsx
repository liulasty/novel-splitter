import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Server, Database, Activity, Clock, ShieldCheck, ListTree, DatabaseZap, Search } from "lucide-react";
import { chromaAdminApi } from "@/api/chromaAdminApi";
import { cn } from "@/lib/utils";
import { toast } from 'sonner';

type TabType = 'system' | 'tenants' | 'collections' | 'records';

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
      </div>

      <div className="mt-6">
        {activeTab === 'system' && <SystemTab />}
        {activeTab === 'tenants' && <TenantsTab />}
        {activeTab === 'collections' && <CollectionsTab />}
        {activeTab === 'records' && <RecordsTab />}
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

  return (
    <Card>
      <CardHeader><CardTitle className="flex items-center gap-2"><Search className="w-5 h-5" />记录检索</CardTitle></CardHeader>
      <CardContent className="space-y-6">
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
