import { useState, useEffect } from 'react';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { novelApi } from '@/api/novelApi';
import { knowledgeApi } from '@/api/knowledgeApi';
import { ragApi } from '@/api/ragApi';
import type { RagDebugResponse, ChatRequest, Scene, ContextBlock } from '@/types/api';
import { estimateTokens } from '@/utils/tokenEstimator';
import { toast } from 'sonner';

export default function RagDebugPage() {
  const [novels, setNovels] = useState<string[]>([]);
  const [versions, setVersions] = useState<string[]>([]);
  
  const [selectedNovel, setSelectedNovel] = useState<string>('');
  const [selectedVersion, setSelectedVersion] = useState<string>('');
  const [question, setQuestion] = useState<string>('');
  const [topK, setTopK] = useState<number>(5);
  
  const [result, setResult] = useState<RagDebugResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    novelApi.getNovels().then(setNovels).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedNovel) {
      knowledgeApi.getVersions(selectedNovel).then(setVersions).catch(console.error);
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
    
    try {
      const request: ChatRequest = {
        question,
        novel: selectedNovel,
        version: selectedVersion,
        topK
      };
      const data = await ragApi.debug(request);
      setResult(data);
    } catch (err: any) {
      setError(err.message || 'Failed to execute debug request');
    } finally {
      setLoading(false);
    }
  };

  const generateFullPrompt = () => {
    if (!result) return '';
    const { systemInstruction, contextBlocks, userQuestion, outputConstraint } = result.finalPrompt;
    
    const parts = [];
    
    if (systemInstruction) {
        parts.push(`=== System Instruction ===\n${systemInstruction}`);
    }
    
    if (contextBlocks && contextBlocks.length > 0) {
        const contextText = contextBlocks.map((b, i) => `[Block ${i+1} - ${b.chunkId}]\n${b.content}`).join('\n\n---\n\n');
        parts.push(`=== Context ===\n${contextText}`);
    }
    
    parts.push(`=== User Question ===\n${userQuestion}`);
    
    if (outputConstraint) {
        parts.push(`=== Output Constraint ===\n${outputConstraint}`);
    }
    
    return parts.join('\n\n');
  };

  const copyFullPrompt = async () => {
    const text = generateFullPrompt();
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      toast.success('已复制完整 Prompt 到剪贴板');
    } catch (err) {
      console.error('Failed to copy:', err);
      toast.error('复制失败，请手动复制');
    }
  };

  return (
    <div className="container mx-auto p-4 space-y-6">
      <h1 className="text-3xl font-bold mb-6">RAG 管道调试 (Debug)</h1>
      
      {/* Input Form */}
      <Card>
        <CardHeader>
          <CardTitle>查询配置</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium mb-1">小说 (Novel)</label>
              <select 
                className="w-full p-2 border rounded"
                value={selectedNovel}
                onChange={(e) => setSelectedNovel(e.target.value)}
              >
                <option value="">选择小说 (可选)</option>
                {novels.map(n => <option key={n} value={n}>{n}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">版本 (Version)</label>
              <select 
                className="w-full p-2 border rounded"
                value={selectedVersion}
                onChange={(e) => setSelectedVersion(e.target.value)}
                disabled={!selectedNovel}
              >
                <option value="">选择版本 (可选)</option>
                {versions.map(v => <option key={v} value={v}>{v}</option>)}
              </select>
            </div>
          </div>
          
          <div>
            <label className="block text-sm font-medium mb-1">问题 (Question)</label>
            <textarea 
              className="w-full p-2 border rounded h-24"
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              placeholder="在此输入问题..."
            />
          </div>
          
          <div>
            <label className="block text-sm font-medium mb-1">Top K (检索数量)</label>
            <input 
              type="number"
              className="w-32 p-2 border rounded"
              value={topK}
              onChange={(e) => setTopK(parseInt(e.target.value))}
              min={1}
              max={50}
            />
          </div>
          
          <button 
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            onClick={handleDebug}
            disabled={loading || !question}
          >
            {loading ? '调试中...' : '开始调试'}
          </button>
          
          {error && <div className="text-red-500 mt-2">{error}</div>}
        </CardContent>
      </Card>

      {/* Results */}
      {result && (
        <div className="space-y-6">
          {/* Stats */}
          <Card>
            <CardHeader>
              <CardTitle>执行统计 (Execution Stats)</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                {Object.entries(result.stats).map(([key, value]) => (
                  <div key={key} className="p-3 bg-gray-50 rounded">
                    <div className="text-sm text-gray-500">{key}</div>
                    <div className="text-lg font-mono">{String(value)}</div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* 1. Retrieval Results */}
          <Card>
            <CardHeader>
              <CardTitle>步骤 1: 原始检索 (Raw Retrieval) - {result.retrievedScenes.length} 条</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4 max-h-[500px] overflow-y-auto">
                {result.retrievedScenes.map((scene, idx) => (
                  <div key={idx} className="p-4 border rounded hover:bg-gray-50">
                    <div className="flex justify-between mb-2">
                      <span className="font-bold text-sm text-gray-600">ID: {scene.id}</span>
                      {/* Note: Raw retrieval score might not be directly available in Scene object unless mapped, assuming Scene has metadata or similar */}
                    </div>
                    <div className="text-sm font-mono bg-gray-100 p-2 rounded mb-2 overflow-x-auto">
                      {JSON.stringify(scene.metadata)}
                    </div>
                    <p className="text-sm whitespace-pre-wrap">{scene.content}</p>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* 2. Assembled Context */}
          <Card>
            <CardHeader>
              <CardTitle>步骤 2: 上下文组装 (Assembled Context) - {result.contextBlocks.length} 块</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-4 max-h-[500px] overflow-y-auto">
                {result.contextBlocks.map((block, idx) => (
                  <div key={idx} className="p-4 border border-green-200 bg-green-50 rounded">
                    <div className="flex justify-between mb-2">
                      <span className="font-bold text-sm text-green-800">切片: {block.chunkId}</span>
                      <span className="text-xs bg-green-200 px-2 py-1 rounded">Token数: {block.tokenCount} | 分数: {block.score.toFixed(4)}</span>
                    </div>
                    <p className="text-sm whitespace-pre-wrap">{block.content}</p>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* 3. Final Prompt */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle>步骤 3: 最终提示词载荷 (Final Prompt Payload)</CardTitle>
              <div className="flex gap-2">
                 <div className="px-3 py-1 text-xs bg-gray-100 text-gray-700 rounded border border-gray-200 flex items-center">
                    <span className="font-bold mr-1">预估 Token:</span> 
                    {estimateTokens(generateFullPrompt())}
                 </div>
                 <button
                   onClick={copyFullPrompt}
                   className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                 >
                   复制完整 Prompt
                 </button>
              </div>
            </CardHeader>
            <CardContent>
              <div className="space-y-6">
                <div>
                   <h3 className="font-bold text-sm mb-2 text-gray-700">完整预览 (Full Preview)</h3>
                   <textarea
                     readOnly
                     value={generateFullPrompt()}
                     className="w-full h-64 p-3 bg-gray-50 border border-gray-200 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                     onClick={(e) => (e.target as HTMLTextAreaElement).select()}
                   />
                </div>
                
                <div className="border-t pt-4 space-y-4">
                    <h3 className="font-bold text-sm text-gray-500 uppercase tracking-wider">详情分解 (Breakdown)</h3>
                    <div>
                      <h3 className="font-bold text-sm mb-1">系统指令 (System Instruction)</h3>
                      <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap">
                        {result.finalPrompt.systemInstruction}
                      </pre>
                    </div>
                    <div>
                      <h3 className="font-bold text-sm mb-1">组装后的上下文 (Assembled Context)</h3>
                      <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap">
                        {result.finalPrompt.contextBlocks.map(b => b.content).join('\n\n')}
                      </pre>
                    </div>
                    <div>
                      <h3 className="font-bold text-sm mb-1">用户问题 (User Question)</h3>
                      <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap">
                        {result.finalPrompt.userQuestion}
                      </pre>
                    </div>
                    <div>
                      <h3 className="font-bold text-sm mb-1">输出约束 (Constraint)</h3>
                      <pre className="bg-gray-900 text-gray-100 p-4 rounded text-sm overflow-x-auto whitespace-pre-wrap">
                        {result.finalPrompt.outputConstraint}
                      </pre>
                    </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
