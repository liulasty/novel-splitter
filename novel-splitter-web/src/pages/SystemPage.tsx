import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Trash2, Search, Loader2 } from "lucide-react";
import { vectorApi } from "@/api/vectorApi";
import { cn } from "@/lib/utils";

export default function SystemPage() {
  const queryClient = useQueryClient();
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // Stats Query
  const { data: stats, isLoading: isStatsLoading } = useQuery({
    queryKey: ['vectorStats'],
    queryFn: vectorApi.getStats,
  });

  // Reset Mutation
  const resetMutation = useMutation({
    mutationFn: vectorApi.reset,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vectorStats'] });
      alert("数据库已清空");
    },
    onError: (error) => {
      alert(`清空失败: ${error}`);
    }
  });

  const handleSearch = async () => {
    if (!searchQuery.trim()) return;
    setIsSearching(true);
    try {
        const results = await vectorApi.search({ query: searchQuery, topK: 5 });
        setSearchResults(results);
    } catch (error) {
        console.error(error);
        alert("搜索失败");
    } finally {
        setIsSearching(false);
    }
  };

  const handleReset = () => {
      if (confirm("确定要清空所有向量数据吗？此操作不可逆！")) {
          resetMutation.mutate();
      }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div className="flex flex-col gap-2">
         <h1 className="text-3xl font-bold tracking-tight text-gray-900">系统管理</h1>
         <p className="text-gray-500">管理 ChromaDB 向量数据库状态。</p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
         {/* Stats Card */}
         <Card>
            <CardHeader className="pb-2">
               <CardTitle className="text-sm font-medium text-gray-500">总文档数</CardTitle>
            </CardHeader>
            <CardContent>
               <div className="text-2xl font-bold">
                   {isStatsLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : stats?.count ?? "--"}
               </div>
               <p className="text-xs text-gray-500 mt-1">
                   {stats?.type || "Vector Store"}
               </p>
            </CardContent>
         </Card>
      </div>

      {/* Vector Search Debugger */}
      <Card>
          <CardHeader>
              <CardTitle className="flex items-center gap-2">
                  <Search className="w-5 h-5" />
                  向量检索调试
              </CardTitle>
              <CardDescription>
                  输入文本测试向量检索结果，验证 Embedding 质量。
              </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
              <div className="flex gap-2">
                  <input 
                    type="text" 
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="flex-1 rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="输入查询文本..."
                    onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
                  />
                  <button 
                    onClick={handleSearch}
                    disabled={isSearching || !searchQuery.trim()}
                    className="bg-blue-600 text-white px-4 py-2 rounded-md text-sm font-medium hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
                  >
                      {isSearching ? <Loader2 className="w-4 h-4 animate-spin" /> : "搜索"}
                  </button>
              </div>

              {searchResults.length > 0 && (
                  <div className="space-y-2 mt-4">
                      <h4 className="text-sm font-medium text-gray-700">检索结果 (Top 5)</h4>
                      <div className="space-y-2">
                          {searchResults.map((result, idx) => (
                              <div key={idx} className="bg-gray-50 p-3 rounded border text-sm">
                                  <div className="flex justify-between text-xs text-gray-500 mb-1">
                                      <span>ID: {result.chunkId}</span>
                                      <span>Score: {result.score.toFixed(4)}</span>
                                  </div>
                              </div>
                          ))}
                      </div>
                  </div>
              )}
          </CardContent>
      </Card>

      <Card className="border-red-100 bg-red-50/30">
         <CardHeader>
            <CardTitle className="text-red-700 flex items-center gap-2">
               <Trash2 className="w-5 h-5" />
               危险操作区
            </CardTitle>
            <CardDescription className="text-red-600/80">
               这些操作不可逆，请谨慎执行。
            </CardDescription>
         </CardHeader>
         <CardContent className="flex items-center justify-between">
            <div>
               <p className="font-medium text-gray-900">清空数据库</p>
               <p className="text-sm text-gray-500">永久删除所有向量数据。</p>
            </div>
            <button 
                onClick={handleReset}
                disabled={resetMutation.isPending}
                className={cn(
                    "bg-red-600 text-white hover:bg-red-700 px-4 py-2 rounded-md text-sm font-medium shadow-sm transition-colors flex items-center gap-2",
                    resetMutation.isPending && "opacity-50 cursor-not-allowed"
                )}
            >
               {resetMutation.isPending && <Loader2 className="w-4 h-4 animate-spin" />}
               确认清空
            </button>
         </CardContent>
      </Card>
    </div>
  );
}
