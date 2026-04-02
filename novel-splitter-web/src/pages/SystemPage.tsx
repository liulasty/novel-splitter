import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Trash2, Search, Loader2, Server, Database, Activity, Clock } from "lucide-react";
import { vectorApi } from "@/api/vectorApi";
import { chromaApi } from "@/api/chromaApi";
import { cn } from "@/lib/utils";
import { toast } from 'sonner';

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

    // Chroma Queries
    const { data: health } = useQuery({
        queryKey: ['chromaHealth'],
        queryFn: chromaApi.getHealthcheck,
        refetchInterval: 30000,
    });

    const { data: version } = useQuery({
        queryKey: ['chromaVersion'],
        queryFn: chromaApi.getVersion,
    });

    const { data: heartbeat } = useQuery({
        queryKey: ['chromaHeartbeat'],
        queryFn: chromaApi.getHeartbeat,
        refetchInterval: 30000,
    });

    const { data: collections, isLoading: isCollectionsLoading } = useQuery({
        queryKey: ['chromaCollections'],
        queryFn: chromaApi.getCollections,
    });

    // Reset Mutation
    const resetMutation = useMutation({
        mutationFn: vectorApi.reset,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['vectorStats'] });
            queryClient.invalidateQueries({ queryKey: ['chromaCollections'] });
            toast.success("数据库已清空");
        },
        onError: (error) => {
            toast.error(`清空失败: ${error}`);
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
            toast.error("搜索失败");
        } finally {
            setIsSearching(false);
        }
    };

    const handleReset = () => {
        toast("确定要清空所有向量数据吗？", {
            description: "此操作不可逆！",
            action: {
                label: "确定清空",
                onClick: () => resetMutation.mutate(),
            },
            cancel: {
                label: "取消",
                onClick: () => {},
            },
        });
    };

    return (
        <div className="max-w-4xl mx-auto space-y-8">
            <div className="flex flex-col gap-2">
                <h1 className="text-3xl font-bold tracking-tight text-gray-900">系统管理</h1>
                <p className="text-gray-500">
                    管理系统后台状态，包括 ChromaDB 向量数据库以及 RabbitMQ 消息队列。
                    <br/>
                    <span className="text-blue-600 font-medium">监控说明：</span> 建议配合 <a href="http://localhost:15672" target="_blank" rel="noreferrer" className="text-blue-800 underline hover:text-blue-600 font-bold">RabbitMQ 管理控制台</a> (默认账密 user/password) 查看详细的队列堆积情况与消费速率。本页面目前主要提供 ChromaDB 数据监控与清空功能。
                </p>
            </div>

            <div className="grid gap-6 md:grid-cols-4">
                {/* Stats Card */}
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
                            <Database className="w-4 h-4" />总文档数
                        </CardTitle>
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

                {/* Health Card - 已修复 */}
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
                            <Activity className="w-4 h-4" />运行状态
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">
                            {health === undefined ? (
                                <Loader2 className="w-6 h-6 animate-spin" />
                            ) : health?.['is_executor_ready'] ? (
                                <span className="text-green-600">在线</span>
                            ) : (
                                <span className="text-red-600">离线</span>
                            )}
                        </div>
                        <p className="text-xs text-gray-500 mt-1">Chroma 服务状态</p>
                    </CardContent>
                </Card>

                {/* Version Card */}
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
                            <Server className="w-4 h-4" />服务版本
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="text-2xl font-bold">
                            {!version ? <Loader2 className="w-6 h-6 animate-spin" /> : version.version ?? "未知"}
                        </div>
                        <p className="text-xs text-gray-500 mt-1">Chroma DB 版本</p>
                    </CardContent>
                </Card>

                {/* Heartbeat Card - 已修复 */}
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm font-medium text-gray-500 flex items-center gap-2">
                            <Clock className="w-4 h-4" />心跳检测
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="text-xl font-bold truncate">
                            {heartbeat === undefined ? (
                                <Loader2 className="w-6 h-6 animate-spin" />
                            ) : heartbeat?.['nanosecond heartbeat'] ? (
                                <span className="text-green-600">正常</span>
                            ) : (
                                <span className="text-red-600">异常</span>
                            )}
                        </div>
                        <p className="text-xs text-gray-500 mt-1 truncate" title={heartbeat?.['nanosecond heartbeat'] ? heartbeat['nanosecond heartbeat'].toString() : ""}>
                            {heartbeat?.['nanosecond heartbeat'] || "--"}
                        </p>
                    </CardContent>
                </Card>
            </div>

            {/* Collections Section */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2">
                        <Database className="w-5 h-5" />
                        集合管理 (Collections)
                    </CardTitle>
                    <CardDescription>
                        当前系统中的所有向量集合
                    </CardDescription>
                </CardHeader>
                <CardContent>
                    {isCollectionsLoading ? (
                        <div className="flex justify-center py-4">
                            <Loader2 className="w-6 h-6 animate-spin text-blue-500" />
                        </div>
                    ) : collections && collections.length > 0 ? (
                        <div className="grid gap-4 md:grid-cols-2">
                            {collections.map((col: any) => (
                                <div key={col.id} className="p-4 rounded-lg border border-gray-200 bg-white shadow-sm flex flex-col gap-2">
                                    <div className="flex justify-between items-start">
                                        <h4 className="font-medium text-gray-900">{col.name}</h4>
                                        <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded-full truncate max-w-[120px]" title={col.id}>
                                      ID: {col.id.substring(0, 8)}...
                                  </span>
                                    </div>
                                    <div className="text-sm text-gray-500 mt-2">
                                        <p>数据库: {col.database}</p>
                                        <p>租户: {col.tenant}</p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="text-center py-8 text-gray-500 border rounded-lg bg-gray-50">
                            暂无集合数据
                        </div>
                    )}
                </CardContent>
            </Card>

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
                        {resetMutation.isPending && <Loader2 className="w-4 h-6 animate-spin" />}
                        确认清空
                    </button>
                </CardContent>
            </Card>
        </div>
    );
}