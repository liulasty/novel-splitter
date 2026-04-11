import { useState, useEffect, useMemo } from 'react';
import { novelApi } from '@/api/novelApi';
import { knowledgeApi } from '@/api/knowledgeApi';
import { ragApi } from '@/api/ragApi';
import { chromaAdminApi } from '@/api/chromaAdminApi';
import type { ChromaCollection } from '@/api/chromaAdminApi';
import type { RagDebugResponse, ChatRequest } from '@/types/api';
import { estimateTokens } from '@/utils/tokenEstimator';
import { toast } from 'sonner';
import { CollapseCard } from '@/components/ui/collapse-card';
import { Copy, Filter, ArrowDownAZ, ArrowUpZA } from 'lucide-react';
import type { NovelSummaryDto } from '@/api/novelApi';

const TABS = [
  { id: 'retrieval', label: '检索结果 (Retrieval)' },
  { id: 'context', label: '上下文组装 (Context)' },
  { id: 'prompt', label: '最终提示词 (Prompt)' }
];

export default function RagDebugPage() {
  const [novels, setNovels] = useState<Array<Pick<NovelSummaryDto, 'novelId' | 'title'>>>([]);
  const [versions, setVersions] = useState<string[]>([]);
  
  const [selectedNovel, setSelectedNovel] = useState<string>(''); // novelId
  const [selectedVersion, setSelectedVersion] = useState<string>('');
  const [question, setQuestion] = useState<string>('');
  const [topK, setTopK] = useState<number>(5);
  
  const [result, setResult] = useState<RagDebugResponse | null>(null);
  
  // ChromaDB Diagnostics State
  const [chromaCollection, setChromaCollection] = useState<ChromaCollection | null>(null);
  const [collectionCount, setCollectionCount] = useState<number | null>(null);
  const [versionRecordCount, setVersionRecordCount] = useState<number | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<string>('retrieval');

  // Filter and Sort states
  const [retrievalFilter, setRetrievalFilter] = useState('');
  const [retrievalSortAsc, setRetrievalSortAsc] = useState(false);
  const [contextFilter, setContextFilter] = useState('');
  const [contextSortAsc, setContextSortAsc] = useState(false);

  useEffect(() => {
    novelApi.getNovelSummaries('embed_ready')
      .then((list) => setNovels(list.map(n => ({ novelId: n.novelId, title: n.title }))))
      .catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedNovel) {
      knowledgeApi.getVersionsByNovelId(selectedNovel).then(setVersions).catch(console.error);
      setSelectedVersion('');
    } else {
      setVersions([]);
    }
  }, [selectedNovel]);

  const handleDebug = async () => {
    if (!question) return;
    
    setLoading(true);
    setError(null);
    setResult(null);
    setChromaCollection(null);
    setCollectionCount(null);
    setVersionRecordCount(null);
    
    try {
      const request: ChatRequest = {
        question,
        novelId: selectedNovel,
        version: selectedVersion,
        topK
      };
      
      const [data] = await Promise.all([
        ragApi.debug(request),
        (async () => {
          try {
            const collections = await chromaAdminApi.getCollections();
            const collection = collections.find(c => c.name === 'novel-splitter') || collections[0];
            if (collection) {
              setChromaCollection(collection);
              const count = await chromaAdminApi.countDocuments(collection.id);
              setCollectionCount(count);
              
              if (selectedNovel && selectedVersion) {
                const records = await chromaAdminApi.getRecords(collection.id, {
                  where: {
                    novelId: selectedNovel,
                    version: selectedVersion
                  },
                  limit: 1,
                  include: ["metadatas", "documents"]
                });
                
                if (records && records.ids && records.ids.length > 0) {
                  setVersionRecordCount(records.ids.length);
                } else {
                  setVersionRecordCount(0);
                }
              }
            }
          } catch (e) {
            console.error('Failed to fetch ChromaDB diagnostics', e);
          }
        })()
      ]);
      
      setResult(data);
      setActiveTab('retrieval');
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to execute debug request');
    } finally {
      setLoading(false);
    }
  };

  const generateFullPrompt = () => {
    if (!result) return '';
    const { systemInstruction, contextBlocks, userQuestion, outputConstraint } = result.finalPrompt;
    
    const parts = [];
    if (systemInstruction) parts.push(`=== System Instruction ===\n${systemInstruction}`);
    if (contextBlocks && contextBlocks.length > 0) {
        const contextText = contextBlocks.map((b, i) => `[Block ${i+1} - ${b.chunkId}]\n${b.content}`).join('\n\n---\n\n');
        parts.push(`=== Context ===\n${contextText}`);
    }
    parts.push(`=== User Question ===\n${userQuestion}`);
    if (outputConstraint) parts.push(`=== Output Constraint ===\n${outputConstraint}`);
    return parts.join('\n\n');
  };

  const copyText = async (text: string, successMsg = '已复制到剪贴板') => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      toast.success(successMsg);
    } catch (err) {
      console.error('Failed to copy:', err);
      toast.error('复制失败，请手动复制');
    }
  };

  // Filtered and sorted data
  const processedRetrieval = useMemo(() => {
    if (!result?.retrievedScenes) return [];
    let list = [...result.retrievedScenes];
    if (retrievalFilter) {
      const lower = retrievalFilter.toLowerCase();
      list = list.filter(s => s.content.toLowerCase().includes(lower) || s.id?.toLowerCase().includes(lower));
    }
    list.sort((a, b) => {
      const scoreA = a.metadata?.score || a.metadata?.distance || 0;
      const scoreB = b.metadata?.score || b.metadata?.distance || 0;
      return retrievalSortAsc ? scoreA - scoreB : scoreB - scoreA;
    });
    return list;
  }, [result?.retrievedScenes, retrievalFilter, retrievalSortAsc]);

  const processedContext = useMemo(() => {
    if (!result?.contextBlocks) return [];
    let list = [...result.contextBlocks];
    if (contextFilter) {
      const lower = contextFilter.toLowerCase();
      list = list.filter(b => b.content.toLowerCase().includes(lower) || b.chunkId.toLowerCase().includes(lower));
    }
    list.sort((a, b) => contextSortAsc ? a.score - b.score : b.score - a.score);
    return list;
  }, [result?.contextBlocks, contextFilter, contextSortAsc]);

  const renderTabContent = () => {
    if (!result) return null;

    switch (activeTab) {
      case 'retrieval':
        return (
          <div className="space-y-4">
            <div className="flex justify-between items-center mb-4 sticky top-0 bg-white z-10 py-2 border-b">
              <h2 className="text-xl font-bold">
                检索结果 (Raw Retrieval) - {result.retrievedScenes.length} 条
                {result.retrievedScenes.length < topK && (
                  <span className="ml-2 text-xs bg-yellow-100 text-yellow-800 px-2 py-1 rounded font-normal">
                    ⚠️ 数量少于期望的 Top K ({topK})
                  </span>
                )}
              </h2>
              <div className="flex items-center gap-3">
                <div className="relative">
                  <Filter className="w-4 h-4 absolute left-2 top-2 text-gray-400" />
                  <input 
                    type="text" 
                    placeholder="按关键词过滤..." 
                    className="pl-8 pr-2 py-1 border rounded text-sm w-48 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    value={retrievalFilter}
                    onChange={e => setRetrievalFilter(e.target.value)}
                  />
                </div>
                <button 
                  onClick={() => setRetrievalSortAsc(!retrievalSortAsc)}
                  className="flex items-center gap-1 px-3 py-1 border rounded text-sm hover:bg-gray-50 transition-colors"
                  title="按分数排序"
                >
                  {retrievalSortAsc ? <ArrowDownAZ className="w-4 h-4" /> : <ArrowUpZA className="w-4 h-4" />}
                  排序
                </button>
              </div>
            </div>
            
            {processedRetrieval.map((scene, idx) => (
              <CollapseCard 
                key={scene.id || idx} 
                title={<span className="text-sm">ID: {scene.id || `Result ${idx+1}`}</span>}
                extra={
                  <button onClick={() => copyText(scene.content)} className="p-1 hover:bg-gray-100 rounded text-gray-500 hover:text-gray-700 transition-colors" title="复制内容">
                    <Copy className="w-4 h-4" />
                  </button>
                }
              >
                <div className="text-xs font-mono bg-gray-100 p-2 rounded mb-3 overflow-x-auto text-gray-600 border border-gray-200">
                  {JSON.stringify(scene.metadata)}
                </div>
                <p className="text-sm whitespace-pre-wrap leading-relaxed text-gray-800">{scene.content}</p>
              </CollapseCard>
            ))}
            {processedRetrieval.length === 0 && (
              <div className="text-center py-10 text-gray-400">没有匹配的结果</div>
            )}
          </div>
        );

      case 'context':
        return (
          <div className="space-y-4">
            <div className="flex justify-between items-center mb-4 sticky top-0 bg-white z-10 py-2 border-b">
              <h2 className="text-xl font-bold">上下文组装 (Assembled Context) - {result.contextBlocks.length} 块</h2>
              <div className="flex items-center gap-3">
                <div className="relative">
                  <Filter className="w-4 h-4 absolute left-2 top-2 text-gray-400" />
                  <input 
                    type="text" 
                    placeholder="按关键词过滤..." 
                    className="pl-8 pr-2 py-1 border rounded text-sm w-48 focus:outline-none focus:ring-1 focus:ring-blue-500"
                    value={contextFilter}
                    onChange={e => setContextFilter(e.target.value)}
                  />
                </div>
                <button 
                  onClick={() => setContextSortAsc(!contextSortAsc)}
                  className="flex items-center gap-1 px-3 py-1 border rounded text-sm hover:bg-gray-50 transition-colors"
                  title="按分数排序"
                >
                  {contextSortAsc ? <ArrowDownAZ className="w-4 h-4" /> : <ArrowUpZA className="w-4 h-4" />}
                  排序
                </button>
              </div>
            </div>
            
            {processedContext.map((block, idx) => (
              <CollapseCard 
                key={block.chunkId || idx} 
                title={
                  <div className="flex items-center gap-3">
                    <span className="text-sm text-green-800 font-bold">切片: {block.chunkId}</span>
                    <span className="text-xs bg-green-100 text-green-700 px-2 py-0.5 rounded border border-green-200">Tokens: {block.tokenCount}</span>
                    <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded border border-blue-200">Score: {block.score.toFixed(4)}</span>
                  </div>
                }
                extra={
                  <button onClick={() => copyText(block.content)} className="p-1 hover:bg-gray-100 rounded text-gray-500 hover:text-gray-700 transition-colors" title="复制内容">
                    <Copy className="w-4 h-4" />
                  </button>
                }
              >
                <p className="text-sm whitespace-pre-wrap leading-relaxed text-gray-800 p-2 bg-green-50/50 rounded border border-green-100">{block.content}</p>
              </CollapseCard>
            ))}
            {processedContext.length === 0 && (
              <div className="text-center py-10 text-gray-400">没有匹配的上下文块</div>
            )}
          </div>
        );

      case 'prompt':
        return (
          <div className="space-y-6">
            <div className="flex justify-between items-center mb-4 sticky top-0 bg-white z-10 py-2 border-b">
              <h2 className="text-xl font-bold">最终提示词载荷 (Final Prompt Payload)</h2>
              <div className="flex gap-3 items-center">
                 <div className="px-3 py-1 text-xs bg-gray-100 text-gray-700 rounded border border-gray-200 flex items-center shadow-sm">
                    <span className="font-bold mr-1">预估 Token:</span> 
                    {estimateTokens(generateFullPrompt())}
                 </div>
                 <button
                   onClick={() => copyText(generateFullPrompt(), '已复制完整 Prompt')}
                   className="flex items-center gap-1 px-3 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors shadow-sm"
                 >
                   <Copy className="w-4 h-4" /> 复制完整 Prompt
                 </button>
              </div>
            </div>
            
            <CollapseCard title={<span className="font-bold text-gray-700">完整预览 (Full Preview)</span>}>
              <textarea
                readOnly
                value={generateFullPrompt()}
                className="w-full h-64 p-3 bg-gray-50 border border-gray-200 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                onClick={(e) => (e.target as HTMLTextAreaElement).select()}
              />
            </CollapseCard>
            
            <div className="space-y-4">
              <h3 className="font-bold text-sm text-gray-500 uppercase tracking-wider pl-1">详情分解 (Breakdown)</h3>
              
              <CollapseCard 
                title={<span className="font-bold text-sm">系统指令 (System Instruction)</span>}
                extra={<button onClick={() => copyText(result.finalPrompt.systemInstruction)} className="p-1 hover:bg-gray-100 rounded text-gray-500"><Copy className="w-4 h-4"/></button>}
              >
                <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap shadow-inner">
                  {result.finalPrompt.systemInstruction}
                </pre>
              </CollapseCard>
              
              <CollapseCard 
                title={<span className="font-bold text-sm">组装后的上下文 (Assembled Context)</span>}
                extra={<button onClick={() => copyText(result.finalPrompt.contextBlocks.map(b => b.content).join('\n\n'))} className="p-1 hover:bg-gray-100 rounded text-gray-500"><Copy className="w-4 h-4"/></button>}
                defaultOpen={false}
              >
                <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap shadow-inner">
                  {result.finalPrompt.contextBlocks.map(b => b.content).join('\n\n')}
                </pre>
              </CollapseCard>
              
              <CollapseCard 
                title={<span className="font-bold text-sm">用户问题 (User Question)</span>}
                extra={<button onClick={() => copyText(result.finalPrompt.userQuestion)} className="p-1 hover:bg-gray-100 rounded text-gray-500"><Copy className="w-4 h-4"/></button>}
              >
                <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap shadow-inner">
                  {result.finalPrompt.userQuestion}
                </pre>
              </CollapseCard>
              
              <CollapseCard 
                title={<span className="font-bold text-sm">输出约束 (Constraint)</span>}
                extra={<button onClick={() => copyText(result.finalPrompt.outputConstraint)} className="p-1 hover:bg-gray-100 rounded text-gray-500"><Copy className="w-4 h-4"/></button>}
              >
                <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap shadow-inner">
                  {result.finalPrompt.outputConstraint}
                </pre>
              </CollapseCard>
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  const renderDiagnosticsAndStats = () => {
    if (!result) return null;
    return (
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4 mb-6">
        {/* Stats Grid */}
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-4">
          <h3 className="text-sm font-bold text-gray-700 mb-3 uppercase tracking-wider">执行统计 (Execution Stats)</h3>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {Object.entries(result.stats).map(([key, value]) => (
              <div key={key} className="p-3 bg-gray-50 border border-gray-100 rounded-lg">
                <div className="text-[10px] text-gray-500 uppercase tracking-wide font-semibold mb-1 truncate">{key}</div>
                <div className="text-lg font-mono font-bold text-blue-700 truncate">{String(value)}</div>
              </div>
            ))}
          </div>
        </div>

        {/* ChromaDB Diagnostics */}
        <div className="bg-white border border-gray-200 rounded-xl shadow-sm p-4">
          <h3 className="text-sm font-bold text-gray-700 mb-3 uppercase tracking-wider">ChromaDB 诊断 (Diagnostics)</h3>
          {!chromaCollection ? (
            <div className="text-center py-4 text-gray-400 text-sm">无诊断数据</div>
          ) : (
            <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
              <div className="flex justify-between border-b border-gray-100 pb-1">
                <span className="text-gray-500">集合:</span>
                <span className="font-mono bg-gray-100 px-1.5 rounded text-xs">{chromaCollection.name}</span>
              </div>
              <div className="flex justify-between border-b border-gray-100 pb-1">
                <span className="text-gray-500">空间:</span>
                <span className={`font-mono text-xs font-bold ${chromaCollection.metadata?.['hnsw:space'] !== 'cosine' ? 'text-red-600' : 'text-green-600'}`}>
                  {chromaCollection.metadata?.['hnsw:space'] || 'l2'}
                </span>
              </div>
              <div className="flex justify-between border-b border-gray-100 pb-1">
                <span className="text-gray-500">总数:</span>
                <span className="font-mono bg-blue-50 text-blue-700 px-1.5 rounded text-xs font-bold">
                  {collectionCount !== null ? collectionCount : '...'}
                </span>
              </div>
              <div className="flex justify-between border-b border-gray-100 pb-1">
                <span className="text-gray-500">当前版本:</span>
                <span className={`font-mono text-xs font-bold ${versionRecordCount === 0 ? 'text-red-600' : 'text-green-600'}`}>
                  {versionRecordCount === null ? '...' : (versionRecordCount > 0 ? `✅ ${versionRecordCount}` : '❌')}
                </span>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="flex flex-col h-screen bg-gray-50 overflow-hidden">
      {/* 顶部固定区域: 查询配置 */}
      <div className="flex-none bg-white border-b shadow-sm z-20 sticky top-0 px-4 py-3">
        <div className="flex flex-col md:flex-row gap-4 max-w-7xl mx-auto items-start md:items-center justify-between">
          <div className="flex items-center gap-4 flex-none">
             <h1 className="text-2xl font-bold text-gray-800 tracking-tight">RAG Debug</h1>
             {loading && <span className="flex h-3 w-3 relative"><span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span><span className="relative inline-flex rounded-full h-3 w-3 bg-blue-500"></span></span>}
          </div>
          
          <div className="flex-1 flex flex-col md:flex-row gap-4 items-center w-full">
            <div className="flex gap-2 w-full md:w-auto">
              <select 
                className="w-full md:w-32 p-2 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 outline-none"
                value={selectedNovel}
                onChange={(e) => setSelectedNovel(e.target.value)}
              >
                <option value="">选择小说...</option>
                {novels.map(n => <option key={n.novelId} value={n.novelId}>{n.title}</option>)}
              </select>
              <select 
                className="w-full md:w-32 p-2 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 outline-none"
                value={selectedVersion}
                onChange={(e) => setSelectedVersion(e.target.value)}
                disabled={!selectedNovel}
              >
                <option value="">选择版本...</option>
                {versions.map(v => <option key={v} value={v}>{v}</option>)}
              </select>
            </div>
            
            <div className="flex-1 w-full">
              <input 
                type="text"
                className="w-full p-2 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 outline-none shadow-inner"
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                placeholder="在此输入问题..."
                onKeyDown={(e) => e.key === 'Enter' && handleDebug()}
              />
            </div>
            
            <div className="flex items-center gap-2 w-full md:w-auto">
              <label className="text-sm text-gray-600 whitespace-nowrap">Top K:</label>
              <input 
                type="number"
                className="w-16 p-2 text-sm border border-gray-300 rounded focus:ring-2 focus:ring-blue-500 outline-none"
                value={topK}
                onChange={(e) => setTopK(parseInt(e.target.value))}
                min={1}
                max={50}
              />
              <button 
                className="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded hover:bg-blue-700 disabled:opacity-50 transition-colors shadow-sm whitespace-nowrap"
                onClick={handleDebug}
                disabled={loading || !question}
              >
                {loading ? '调试中...' : '开始调试'}
              </button>
            </div>
          </div>
        </div>
        {error && <div className="text-red-500 text-sm mt-2 max-w-7xl mx-auto font-medium bg-red-50 p-2 rounded border border-red-100">{error}</div>}
      </div>

      {/* 下方区域: 左右分栏 */}
      <div className="flex-1 flex overflow-hidden max-w-7xl mx-auto w-full p-4 gap-6">
        {/* 左侧: Tab 列表 */}
        <div className="w-56 flex-none flex flex-col gap-2 overflow-y-auto pr-2 pb-4">
          <div className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-2 px-2">模块导航</div>
          {TABS.map(tab => (
            <button
              key={tab.id}
              className={`w-full text-left px-4 py-3 rounded-lg text-sm font-medium transition-all duration-200 ${
                activeTab === tab.id 
                  ? 'bg-blue-600 text-white shadow-md transform scale-[1.02]' 
                  : 'bg-white hover:bg-gray-100 text-gray-600 border border-gray-200'
              } ${!result ? 'opacity-50 cursor-not-allowed' : ''}`}
              onClick={() => result && setActiveTab(tab.id)}
              disabled={!result}
            >
              {tab.label}
            </button>
          ))}
          
          {!result && !loading && (
            <div className="mt-8 p-4 bg-blue-50 border border-blue-100 rounded-lg text-sm text-blue-800">
              <p className="font-bold mb-1">使用提示</p>
              <p className="opacity-80 leading-relaxed">请在顶部选择小说、版本，输入问题并点击“开始调试”按钮查看 RAG 各个环节的数据。</p>
            </div>
          )}
        </div>

        {/* 右侧: Tab 内容 (具有内部滚动条) */}
        <div className="flex-1 flex flex-col overflow-hidden relative">
          {result ? (
            <div className="flex-1 overflow-y-auto custom-scrollbar flex flex-col gap-4">
              {renderDiagnosticsAndStats()}
              <div className="flex-1 bg-white rounded-xl shadow-sm border border-gray-200 p-6">
                {renderTabContent()}
              </div>
            </div>
          ) : (
            <div className="flex-1 bg-white rounded-xl shadow-sm border border-gray-200 flex items-center justify-center flex-col text-gray-400 p-6">
              <div className="w-16 h-16 bg-gray-100 rounded-full flex items-center justify-center mb-4">
                <Filter className="w-8 h-8 text-gray-300" />
              </div>
              <p className="text-lg font-medium text-gray-500">等待调试结果...</p>
              <p className="text-sm mt-2">执行调试后，结果将在此处展示。</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
