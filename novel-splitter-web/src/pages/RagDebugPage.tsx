import { useState, useEffect, useMemo } from 'react';
import { novelApi } from '@/api/novelApi';
import { knowledgeApi, type SceneSplitProfileDto } from '@/api/knowledgeApi';
import { ragApi } from '@/api/ragApi';
import { chromaAdminApi } from '@/api/chromaAdminApi';
import type { ChromaCollection } from '@/api/chromaAdminApi';
import type { RagDebugResponse, ChatRequest } from '@/types/api';
import { estimateTokens } from '@/utils/tokenEstimator';
import { toast } from 'sonner';
import { CollapseCard } from '@/components/ui/collapse-card';
import { SelectMenu, type SelectMenuOption } from '@/components/ui/select-menu';
import { cn } from '@/lib/utils';
import { Copy, Filter, ArrowDownAZ, ArrowUpZA, BugPlay } from 'lucide-react';
import type { NovelSummaryDto } from '@/api/novelApi';

const TABS = [
  { id: 'retrieval', label: '检索结果 (Retrieval)' },
  { id: 'context', label: '上下文组装 (Context)' },
  { id: 'prompt', label: '最终提示词 (Prompt)' }
];

function RagDebugPage() {
  const [novels, setNovels] = useState<Array<Pick<NovelSummaryDto, 'novelId' | 'title' | 'status'>>>([]);
  const [splitProfiles, setSplitProfiles] = useState<SceneSplitProfileDto[]>([]);
  const [selectedProfileIndex, setSelectedProfileIndex] = useState(0);

  const [selectedNovel, setSelectedNovel] = useState<string>(''); // novelId
  const [question, setQuestion] = useState<string>('');
  const [topK, setTopK] = useState<number>(5);
  const [maxScenes, setMaxScenes] = useState<number>(5);
  const [maxContextTokens, setMaxContextTokens] = useState<number>(3000);
  const [maxAnswerTokens, setMaxAnswerTokens] = useState<number>(0);
  
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
      .then((list) => setNovels(list.map(n => ({ novelId: n.novelId, title: n.title, status: n.status }))))
      .catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedNovel) {
      knowledgeApi
        .listSplitProfilesByNovelId(selectedNovel)
        .then((list) => {
          setSplitProfiles(list);
          setSelectedProfileIndex(list.length ? list.length - 1 : 0);
        })
        .catch(console.error);
    } else {
      setSplitProfiles([]);
      setSelectedProfileIndex(0);
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
      const profile = splitProfiles[selectedProfileIndex];
      if (!profile?.version) {
        setError('请选择有效的数据集（版本 / 滑窗）');
        setLoading(false);
        return;
      }
      const request: ChatRequest = {
        question,
        novelId: selectedNovel,
        version: profile.version,
        topK,
        chunkSize: profile.chunkSize ?? undefined,
        chunkOverlap: profile.chunkOverlap ?? undefined,
        maxScenes,
        maxContextTokens,
        maxAnswerTokens: maxAnswerTokens > 0 ? maxAnswerTokens : undefined,
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
              
              const prof = splitProfiles[selectedProfileIndex];
              if (selectedNovel && prof?.version) {
                const andClauses: Record<string, { $eq: string | number }>[] = [
                  { novelId: { $eq: selectedNovel } },
                  { version: { $eq: prof.version } },
                ];
                if (prof.chunkSize != null && prof.chunkOverlap != null) {
                  andClauses.push({ chunkSize: { $eq: prof.chunkSize } });
                  andClauses.push({ chunkOverlap: { $eq: prof.chunkOverlap } });
                }
                const records = await chromaAdminApi.getRecords(collection.id, {
                  where: { $and: andClauses },
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
        const contextText = contextBlocks.map((b, i) => {
          const chapterTitle: string = (b.sceneMetadata as Record<string, unknown>)?.['chapterTitle'] as string
            || (b.metadata?.['chapterTitle'] as string) || '';
          const header = chapterTitle
            ? `[Block ${i + 1} — ${chapterTitle}] (chunkId: ${b.chunkId})`
            : `[Block ${i + 1} — ${b.chunkId}]`;
          return `${header}\n${b.content}`;
        }).join('\n\n---\n\n');
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

  const novelOptions: SelectMenuOption[] = useMemo(
    () => novels.map((n) => ({
      value: n.novelId,
      label: n.title,
      description: n.status ? `状态: ${n.status}` : undefined,
    })),
    [novels]
  );

  const profileOptions: SelectMenuOption[] = useMemo(
    () =>
      splitProfiles.map((p, i) => ({
        value: String(i),
        label: p.version,
        description: p.chunkSize != null && p.chunkOverlap != null
          ? `chunk: ${p.chunkSize} / overlap: ${p.chunkOverlap}`
          : undefined,
        badge: p.chunkSize != null && p.chunkOverlap != null
          ? `${p.chunkSize}/${p.chunkOverlap}`
          : undefined,
      })),
    [splitProfiles]
  );

  const inputClass =
    'w-full rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm text-slate-800 shadow-sm transition-colors placeholder:text-slate-400 hover:border-slate-300 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500/25 focus-visible:border-indigo-400';

  const renderTabContent = () => {
    if (!result) return null;

    switch (activeTab) {
      case 'retrieval':
        return (
          <div className="space-y-4">
            <div className="flex justify-between items-center mb-4 sticky top-0 z-10 border-b border-slate-200/80 bg-white/95 py-2 backdrop-blur-sm">
              <h2 className="text-xl font-bold text-slate-900">
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
                    className={cn('pl-8 pr-2 py-1.5 w-48', inputClass)}
                    value={retrievalFilter}
                    onChange={e => setRetrievalFilter(e.target.value)}
                  />
                </div>
                <button 
                  type="button"
                  onClick={() => setRetrievalSortAsc(!retrievalSortAsc)}
                  className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
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
            <div className="flex justify-between items-center mb-4 sticky top-0 z-10 border-b border-slate-200/80 bg-white/95 py-2 backdrop-blur-sm">
              <h2 className="text-xl font-bold text-slate-900">上下文组装 (Assembled Context) - {result.contextBlocks.length} 块</h2>
              <div className="flex items-center gap-3">
                <div className="relative">
                  <Filter className="w-4 h-4 absolute left-2 top-2 text-gray-400" />
                  <input 
                    type="text" 
                    placeholder="按关键词过滤..." 
                    className={cn('pl-8 pr-2 py-1.5 w-48', inputClass)}
                    value={contextFilter}
                    onChange={e => setContextFilter(e.target.value)}
                  />
                </div>
                <button 
                  type="button"
                  onClick={() => setContextSortAsc(!contextSortAsc)}
                  className="flex items-center gap-1 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-sm text-slate-700 shadow-sm transition-colors hover:bg-slate-50"
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
            <div className="flex justify-between items-center mb-4 sticky top-0 z-10 border-b border-slate-200/80 bg-white/95 py-2 backdrop-blur-sm">
              <h2 className="text-xl font-bold text-slate-900">最终提示词载荷 (Final Prompt Payload)</h2>
              <div className="flex gap-3 items-center">
                 <div className="px-3 py-1 text-xs bg-gray-100 text-gray-700 rounded border border-gray-200 flex items-center shadow-sm">
                    <span className="font-bold mr-1">预估 Token:</span> 
                    {estimateTokens(generateFullPrompt())}
                 </div>
                 <button
                   type="button"
                   onClick={() => copyText(generateFullPrompt(), '已复制完整 Prompt')}
                   className="flex items-center gap-1 rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-indigo-700"
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
    <div className="flex h-screen flex-col overflow-hidden bg-slate-50">
      <header className="z-20 flex-none border-b border-slate-200/80 bg-white/90 px-4 py-4 shadow-sm shadow-slate-200/40 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl flex-col gap-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-100">
                <BugPlay className="h-5 w-5 text-indigo-600" aria-hidden />
              </div>
              <div>
                <h1 className="text-xl font-bold tracking-tight text-slate-900 sm:text-2xl">RAG Debug</h1>
                <p className="text-xs text-slate-500 sm:text-sm">检索 → 上下文 → 提示词，逐步对照</p>
              </div>
              {loading && (
                <span className="relative flex h-3 w-3" aria-hidden>
                  <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-indigo-400 opacity-75" />
                  <span className="relative inline-flex h-3 w-3 rounded-full bg-indigo-500" />
                </span>
              )}
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(0,11rem)_minmax(0,14rem)_1fr_auto] lg:items-end">
            <div className="space-y-1.5">
              <span className="block text-xs font-medium text-slate-500">小说</span>
              <SelectMenu
                value={selectedNovel}
                onValueChange={setSelectedNovel}
                options={novelOptions}
                placeholder="选择已向量化的小说…"
                className="w-full min-w-0 lg:min-w-[11rem]"
                emptyMessage={novels.length ? '暂无可选' : '加载中或暂无书目'}
              />
            </div>
            <div className="space-y-1.5">
              <span className="block text-xs font-medium text-slate-500">数据集 / 滑窗</span>
              <SelectMenu
                value={splitProfiles.length ? String(selectedProfileIndex) : ''}
                onValueChange={(v) => setSelectedProfileIndex(Number(v))}
                options={profileOptions}
                placeholder={selectedNovel ? '选择切片配置…' : '请先选择小说'}
                disabled={!selectedNovel || !splitProfiles.length}
                className="w-full min-w-0 lg:min-w-[14rem]"
                emptyMessage="该书暂无切片配置"
              />
            </div>
            <div className="space-y-1.5 min-w-0 lg:col-span-1">
              <label htmlFor="rag-debug-question" className="block text-xs font-medium text-slate-500">
                问题
              </label>
              <input
                id="rag-debug-question"
                type="text"
                className={inputClass}
                value={question}
                onChange={(e) => setQuestion(e.target.value)}
                placeholder="输入要向量化库查询的问题…"
                onKeyDown={(e) => e.key === 'Enter' && handleDebug()}
              />
            </div>
            <div className="flex flex-wrap items-end gap-2 lg:flex-nowrap">
              <div className="space-y-1.5">
                <label htmlFor="rag-debug-topk" className="block text-xs font-medium text-slate-500">
                  Top K
                </label>
                <input
                  id="rag-debug-topk"
                  type="number"
                  className={cn(inputClass, 'w-[4.5rem] text-center tabular-nums')}
                  value={topK}
                  onChange={(e) => {
                    const v = parseInt(e.target.value, 10);
                    if (Number.isNaN(v)) setTopK(5);
                    else setTopK(Math.min(50, Math.max(1, v)));
                  }}
                  min={1}
                  max={50}
                />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="rag-debug-maxscenes" className="block text-xs font-medium text-slate-500">
                  场景上限
                </label>
                <input
                  id="rag-debug-maxscenes"
                  type="number"
                  className={cn(inputClass, 'w-[4.5rem] text-center tabular-nums')}
                  value={maxScenes}
                  onChange={(e) => {
                    const v = parseInt(e.target.value, 10);
                    if (Number.isNaN(v)) setMaxScenes(5);
                    else setMaxScenes(Math.min(50, Math.max(1, v)));
                  }}
                  min={1}
                  max={50}
                />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="rag-debug-tokens" className="block text-xs font-medium text-slate-500">
                  Token
                </label>
                <input
                  id="rag-debug-tokens"
                  type="number"
                  className={cn(inputClass, 'w-[5rem] text-center tabular-nums')}
                  value={maxContextTokens}
                  onChange={(e) => {
                    const v = parseInt(e.target.value, 10);
                    if (Number.isNaN(v)) setMaxContextTokens(3000);
                    else setMaxContextTokens(Math.min(16000, Math.max(500, v)));
                  }}
                  min={500}
                  max={16000}
                />
              </div>
              <div className="space-y-1.5">
                <label htmlFor="rag-debug-answertokens" className="block text-xs font-medium text-slate-500">
                  回答
                </label>
                <input
                  id="rag-debug-answertokens"
                  type="number"
                  className={cn(inputClass, 'w-[5rem] text-center tabular-nums')}
                  value={maxAnswerTokens}
                  onChange={(e) => {
                    const v = parseInt(e.target.value, 10);
                    if (Number.isNaN(v)) setMaxAnswerTokens(0);
                    else setMaxAnswerTokens(Math.min(4000, Math.max(0, v)));
                  }}
                  min={0}
                  max={4000}
                />
              </div>
              <button
                type="button"
                className="inline-flex h-[42px] shrink-0 items-center justify-center rounded-lg bg-indigo-600 px-5 text-sm font-medium text-white shadow-sm transition-colors hover:bg-indigo-700 disabled:cursor-not-allowed disabled:opacity-50"
                onClick={handleDebug}
                disabled={loading || !question}
              >
                {loading ? '调试中…' : '开始调试'}
              </button>
            </div>
          </div>

          {error && (
            <div
              role="alert"
              className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-700"
            >
              {error}
            </div>
          )}
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-7xl flex-1 gap-6 overflow-hidden p-4">
        <aside className="flex w-56 flex-none flex-col gap-2 overflow-y-auto pb-4 pr-1">
          <div className="mb-1 px-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
            模块导航
          </div>
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={cn(
                'w-full rounded-xl px-4 py-3 text-left text-sm font-medium transition-all duration-200',
                activeTab === tab.id
                  ? 'scale-[1.02] bg-indigo-600 text-white shadow-md shadow-indigo-600/25'
                  : 'border border-slate-200/80 bg-white text-slate-600 shadow-sm hover:border-slate-300 hover:bg-slate-50',
                !result && 'cursor-not-allowed opacity-45 hover:bg-white'
              )}
              onClick={() => result && setActiveTab(tab.id)}
              disabled={!result}
            >
              {tab.label}
            </button>
          ))}

          {!result && !loading && (
            <div className="mt-6 rounded-xl border border-indigo-100 bg-indigo-50/80 p-4 text-sm text-indigo-900">
              <p className="mb-1 font-semibold">使用提示</p>
              <p className="leading-relaxed text-indigo-800/90">
                选择小说与数据集，输入问题后点击「开始调试」即可查看检索、上下文与最终 Prompt。
              </p>
            </div>
          )}
        </aside>

        <main className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
          {result ? (
            <div className="custom-scrollbar flex flex-1 flex-col gap-4 overflow-y-auto">
              {renderDiagnosticsAndStats()}
              <div className="flex-1 rounded-2xl border border-slate-200/80 bg-white p-6 shadow-sm shadow-slate-200/30">
                {renderTabContent()}
              </div>
            </div>
          ) : (
            <div className="flex flex-1 flex-col items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/80 p-8 text-slate-400 shadow-sm">
              <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100">
                <Filter className="h-8 w-8 text-slate-300" aria-hidden />
              </div>
              <p className="text-lg font-medium text-slate-600">等待调试结果</p>
              <p className="mt-2 max-w-sm text-center text-sm text-slate-500">
                提交查询后，统计与分栏内容会显示在这里。
              </p>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

export default RagDebugPage;
