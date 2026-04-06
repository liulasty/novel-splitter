import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Server, Database, Activity, Clock, CheckCircle2, XCircle, ArrowRight } from "lucide-react";
import { vectorApi } from "@/api/vectorApi";
import { chromaApi } from "@/api/chromaApi";
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import { cn } from "@/lib/utils";

export default function SystemPage() {
    const queryClient = useQueryClient();

    // Stats Query
    const { data: vectorStats, isLoading: isVectorStatsLoading } = useQuery({
        queryKey: ['vectorStats'],
        queryFn: vectorApi.getStats,
    });

    const { data: dashboardStats, isLoading: isDashboardStatsLoading } = useQuery({
        queryKey: ['dashboardStats'],
        queryFn: novelApi.getDashboardStats,
    });

    const { data: modelHealth, isLoading: isModelHealthLoading } = useQuery({
        queryKey: ['modelHealth'],
        queryFn: novelApi.getModelHealth,
        refetchInterval: 30000,
    });

    const { data: novels, isLoading: isNovelsLoading } = useQuery({
        queryKey: ['novels'],
        queryFn: novelApi.getNovels,
    });

    const { data: tasks, isLoading: isTasksLoading } = useQuery({
        queryKey: ['tasks'],
        queryFn: taskApi.getAllTasks,
        refetchInterval: 5000,
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
        // ✅ 关键：自动解析 JSON 字符串为数组
        select: (res) => {
            try {
                return JSON.parse(res.data);
            } catch (e) {
                return []; // 解析失败返回空数组，防止报错
            }
        },
    });

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
                {/* Stats Cards */}
                <Card className="bg-indigo-50/50 border-indigo-100">
                    <CardContent className="p-6">
                        <div className="text-xs font-semibold text-indigo-500 uppercase tracking-wider mb-2">入库小说</div>
                        <div className="text-3xl font-bold text-indigo-900">
                            {isNovelsLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : novels?.length ?? 0}
                        </div>
                        <div className="text-xs text-indigo-600/70 mt-2">已入库版本</div>
                    </CardContent>
                </Card>

                <Card className="bg-emerald-50/50 border-emerald-100">
                    <CardContent className="p-6">
                        <div className="text-xs font-semibold text-emerald-500 uppercase tracking-wider mb-2">向量总数</div>
                        <div className="text-3xl font-bold text-emerald-900">
                            {isVectorStatsLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : vectorStats?.count ?? 0}
                        </div>
                        <div className="text-xs text-emerald-600/70 mt-2">ChromaDB</div>
                    </CardContent>
                </Card>

                <Card className="bg-blue-50/50 border-blue-100">
                    <CardContent className="p-6">
                        <div className="text-xs font-semibold text-blue-500 uppercase tracking-wider mb-2">问答次数</div>
                        <div className="text-3xl font-bold text-blue-900">
                            {isDashboardStatsLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : dashboardStats?.qaCount ?? 0}
                        </div>
                        <div className="text-xs text-blue-600/70 mt-2">今日 {dashboardStats?.todayQaCount ?? 0} 次</div>
                    </CardContent>
                </Card>

                <Card className="bg-amber-50/50 border-amber-100">
                    <CardContent className="p-6">
                        <div className="text-xs font-semibold text-amber-500 uppercase tracking-wider mb-2">平均检索耗时</div>
                        <div className="text-3xl font-bold text-amber-900">
                            {isDashboardStatsLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : `${dashboardStats?.avgRetrievalTimeMs ?? 0}ms`}
                        </div>
                        <div className="text-xs text-amber-600/70 mt-2">{dashboardStats?.retrievalTimeTrend ?? '--'}</div>
                    </CardContent>
                </Card>
            </div>

            <div className="grid gap-6 md:grid-cols-2">
                {/* Recent Tasks */}
                <Card>
                    <CardHeader className="flex flex-row items-center justify-between pb-2 border-b border-gray-100">
                        <CardTitle className="text-base">近期入库任务</CardTitle>
                        <span className="bg-emerald-100 text-emerald-700 text-xs font-medium px-2.5 py-0.5 rounded-full">
                            {tasks?.filter(t => t.status === 'PROCESSING').length || 0} 运行中
                        </span>
                    </CardHeader>
                    <CardContent className="p-0">
                        <div className="overflow-x-auto">
                            <table className="w-full text-sm text-left">
                                <thead className="text-xs text-gray-500 bg-gray-50/50 border-b border-gray-100">
                                    <tr>
                                        <th className="px-4 py-3 font-medium">小说</th>
                                        <th className="px-4 py-3 font-medium">阶段</th>
                                        <th className="px-4 py-3 font-medium">进度</th>
                                        <th className="px-4 py-3 font-medium text-center">状态</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-gray-100">
                                    {isTasksLoading ? (
                                        <tr><td colSpan={4} className="text-center py-8 text-gray-500"><Loader2 className="w-6 h-6 animate-spin mx-auto" /></td></tr>
                                    ) : tasks?.slice(0, 4).map((task) => (
                                        <tr key={task.taskId} className="hover:bg-gray-50/50">
                                            <td className="px-4 py-3">
                                                <div className="font-medium text-gray-900">{task.novelId}</div>
                                                <div className="text-xs text-gray-500 font-mono mt-0.5">{task.version}</div>
                                            </td>
                                            <td className="px-4 py-3">
                                                <span className={cn(
                                                    "text-xs font-medium px-2 py-0.5 rounded-full",
                                                    task.taskType === 'EMBED' ? "bg-indigo-100 text-indigo-700" : "bg-blue-100 text-blue-700"
                                                )}>
                                                    {task.taskType === 'EMBED' ? '向量化' : '切分'}
                                                </span>
                                            </td>
                                            <td className="px-4 py-3">
                                                <div className="flex items-center gap-2">
                                                    <div className="w-16 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                                                        <div 
                                                            className={cn("h-full rounded-full", task.status === 'FAILED' ? "bg-red-500" : "bg-blue-500")} 
                                                            style={{ width: `${task.progress}%` }} 
                                                        />
                                                    </div>
                                                    <span className="text-xs text-gray-500 font-mono">{task.progress}%</span>
                                                </div>
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                {task.status === 'PROCESSING' && <Loader2 className="w-4 h-4 animate-spin text-blue-500 mx-auto" />}
                                                {task.status === 'SUCCESS' && <CheckCircle2 className="w-4 h-4 text-emerald-500 mx-auto" />}
                                                {task.status === 'FAILED' && <XCircle className="w-4 h-4 text-red-500 mx-auto" />}
                                                {task.status === 'PENDING' && <Clock className="w-4 h-4 text-gray-400 mx-auto" />}
                                            </td>
                                        </tr>
                                    ))}
                                    {tasks?.length === 0 && (
                                        <tr><td colSpan={4} className="text-center py-8 text-gray-500">暂无任务</td></tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    </CardContent>
                </Card>

                {/* Chroma Health & Model Health */}
                <Card>
                    <CardHeader className="pb-2 border-b border-gray-100">
                        <CardTitle className="text-base">ChromaDB 健康状态</CardTitle>
                    </CardHeader>
                    <CardContent className="p-0">
                        <div className="divide-y divide-gray-100">
                            <div className="flex justify-between items-center px-6 py-3">
                                <div>
                                    <div className="text-sm font-medium text-gray-900">Collection</div>
                                    <div className="text-xs text-gray-500">novel-splitter</div>
                                </div>
                                <span className="bg-emerald-100 text-emerald-700 text-xs font-medium px-2.5 py-0.5 rounded-full">在线</span>
                            </div>
                            <div className="flex justify-between items-center px-6 py-3">
                                <div>
                                    <div className="text-sm font-medium text-gray-900">总向量数</div>
                                    <div className="text-xs text-gray-500">所有版本合计</div>
                                </div>
                                <span className="font-mono text-sm font-medium text-indigo-600">{vectorStats?.count ?? '--'}</span>
                            </div>
                            <div className="flex justify-between items-center px-6 py-3">
                                <div>
                                    <div className="text-sm font-medium text-gray-900">Embedding 模型</div>
                                    <div className="text-xs text-gray-500">本地 ONNX</div>
                                </div>
                                {isModelHealthLoading ? <Loader2 className="w-4 h-4 animate-spin text-gray-400" /> : (
                                    <span className={cn("text-xs font-medium px-2.5 py-0.5 rounded-full", modelHealth?.embeddingModelLoaded ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700")}>
                                        {modelHealth?.embeddingModelLoaded ? "已加载" : "未加载"}
                                    </span>
                                )}
                            </div>
                            <div className="flex justify-between items-center px-6 py-3">
                                <div>
                                    <div className="text-sm font-medium text-gray-900">LLM 后端</div>
                                    <div className="text-xs text-gray-500">API 连接性</div>
                                </div>
                                {isModelHealthLoading ? <Loader2 className="w-4 h-4 animate-spin text-gray-400" /> : (
                                    <span className={cn("text-xs font-medium px-2.5 py-0.5 rounded-full", modelHealth?.llmBackendReachable ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700")}>
                                        {modelHealth?.llmBackendReachable ? "可达" : "不可达"}
                                    </span>
                                )}
                            </div>
                        </div>
                    </CardContent>
                </Card>
            </div>

            {/* Pipeline Data Flow */}
            <Card>
                <CardHeader className="pb-4 border-b border-gray-100">
                    <CardTitle className="text-base">入库数据流向</CardTitle>
                </CardHeader>
                <CardContent className="p-6">
                    <div className="flex flex-col gap-6">
                        <div className="flex items-center gap-2 overflow-x-auto pb-2">
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">下载/读取</span>
                                <span className="text-[10px] text-gray-500 font-mono">novelDownloader</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">切分</span>
                                <span className="text-[10px] text-gray-500 font-mono">splitter</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">校验</span>
                                <span className="text-[10px] text-gray-500 font-mono">validation</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-indigo-200 bg-indigo-50 min-w-[100px]">
                                <span className="text-xs font-bold text-indigo-700">向量化</span>
                                <span className="text-[10px] text-gray-500 font-mono">embedding</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-gray-200 bg-gray-50 min-w-[100px] opacity-70">
                                <span className="text-xs font-bold text-gray-700">入库</span>
                                <span className="text-[10px] text-gray-500 font-mono">repository</span>
                            </div>
                        </div>

                        <div className="h-px bg-gray-100" />

                        <div className="flex items-center gap-2 overflow-x-auto pb-2">
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">问题输入</span>
                                <span className="text-[10px] text-gray-500 font-mono">application</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">向量化</span>
                                <span className="text-[10px] text-gray-500 font-mono">embedding</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">召回</span>
                                <span className="text-[10px] text-gray-500 font-mono">retrieval</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">组装</span>
                                <span className="text-[10px] text-gray-500 font-mono">context-assembler</span>
                            </div>
                            <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                            <div className="flex-shrink-0 flex flex-col items-center gap-1.5 p-3 rounded-lg border border-emerald-200 bg-emerald-50 min-w-[100px]">
                                <span className="text-xs font-bold text-emerald-700">生成</span>
                                <span className="text-[10px] text-gray-500 font-mono">llm-client</span>
                            </div>
                        </div>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}