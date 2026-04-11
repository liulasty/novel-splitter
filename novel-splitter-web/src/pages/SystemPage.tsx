import { useQuery } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardContent } from "@/components/ui/card";
import {
    Loader2,
    Clock,
    CheckCircle2,
    XCircle,
    BookOpen,
    Database,
    Cpu,
    ExternalLink,
    Activity,
} from "lucide-react";
import { vectorApi } from "@/api/vectorApi";
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import type { SplitTask } from "@/api/taskApi";
import { cn } from "@/lib/utils";

const RABBITMQ_CONSOLE = "http://localhost:15672";

const INGEST_PIPELINE: { label: string; module: string; tone: "emerald" | "indigo" | "muted" }[] = [
    { label: "下载/读取", module: "novelDownloader", tone: "emerald" },
    { label: "切分", module: "splitter", tone: "emerald" },
    { label: "校验", module: "validation", tone: "emerald" },
    { label: "向量化", module: "embedding", tone: "indigo" },
    { label: "入库", module: "repository", tone: "muted" },
];

const QA_PIPELINE: { label: string; module: string }[] = [
    { label: "问题输入", module: "application" },
    { label: "向量化", module: "embedding" },
    { label: "召回", module: "retrieval" },
    { label: "组装", module: "context-assembler" },
    { label: "生成", module: "llm-client" },
];

function taskPhaseLabel(taskType: SplitTask["taskType"]) {
    return taskType === "EMBED" ? "向量化" : "切分";
}

function TaskStatusIcon({ status }: { status: SplitTask["status"] }) {
    switch (status) {
        case "PROCESSING":
            return <Loader2 className="h-4 w-4 shrink-0 animate-spin text-blue-500" aria-hidden />;
        case "SUCCESS":
            return <CheckCircle2 className="h-4 w-4 shrink-0 text-emerald-500" aria-hidden />;
        case "FAILED":
            return <XCircle className="h-4 w-4 shrink-0 text-red-500" aria-hidden />;
        default:
            return <Clock className="h-4 w-4 shrink-0 text-gray-400" aria-hidden />;
    }
}

function PipelineStep({
    label,
    module,
    tone = "emerald",
}: {
    label: string;
    module: string;
    tone?: "emerald" | "indigo" | "muted";
}) {
    const shell =
        tone === "indigo"
            ? "border-indigo-200/80 bg-indigo-50/90"
            : tone === "muted"
              ? "border-gray-200/90 bg-gray-50/80 opacity-90"
              : "border-emerald-200/80 bg-emerald-50/90";
    const title =
        tone === "indigo"
            ? "text-indigo-800"
            : tone === "muted"
              ? "text-gray-800"
              : "text-emerald-800";

    return (
        <div
            className={cn(
                "flex min-h-[4.25rem] min-w-0 flex-col justify-center rounded-lg border px-2 py-2.5 text-center shadow-sm",
                shell,
            )}
        >
            <span className={cn("text-xs font-semibold leading-tight", title)}>{label}</span>
            <span className="mt-1 truncate font-mono text-[10px] leading-none text-gray-500" title={module}>
                {module}
            </span>
        </div>
    );
}

export default function SystemPage() {
    const { data: vectorStats, isLoading: isVectorStatsLoading } = useQuery({
        queryKey: ["vectorStats"],
        queryFn: vectorApi.getStats,
    });

    const { data: modelHealth, isLoading: isModelHealthLoading } = useQuery({
        queryKey: ["modelHealth"],
        queryFn: novelApi.getModelHealth,
        refetchInterval: 30000,
    });

    const { data: novels, isLoading: isNovelsLoading } = useQuery({
        queryKey: ["novelSummaries", "all"],
        queryFn: () => novelApi.getNovelSummaries("all"),
    });

    const { data: tasks, isLoading: isTasksLoading } = useQuery({
        queryKey: ["tasks"],
        queryFn: taskApi.getAllTasks,
        refetchInterval: 5000,
    });

    const titleById = new Map((novels ?? []).map((n) => [n.novelId, n.title] as const));
    const running = tasks?.filter((t) => t.status === "PROCESSING").length ?? 0;
    const recentTasks = tasks?.slice(0, 6) ?? [];

    return (
        <div className="mx-auto w-full min-w-0 max-w-6xl space-y-10 pb-10">
            <header className="space-y-4 border-b border-gray-100 pb-8">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
                    <div className="min-w-0 space-y-1">
                        <h1 className="text-3xl font-bold tracking-tight text-gray-900">系统管理</h1>
                        <p className="max-w-2xl text-sm leading-relaxed text-gray-500">
                            查看 ChromaDB 与模型侧状态、近期入库任务与流水线结构。队列堆积与消费速率请在 RabbitMQ 控制台核对。
                        </p>
                    </div>
                    <a
                        href={RABBITMQ_CONSOLE}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-medium text-gray-700 shadow-sm transition hover:border-gray-300 hover:bg-gray-50"
                    >
                        RabbitMQ 控制台
                        <ExternalLink className="h-3.5 w-3.5 text-gray-400" aria-hidden />
                    </a>
                </div>
                <p className="text-xs text-gray-400">
                    默认访问{" "}
                    <span className="font-mono text-gray-500">
                        {RABBITMQ_CONSOLE}
                    </span>{" "}
                    ，账密一般为 <span className="font-mono">user</span> / <span className="font-mono">password</span>
                </p>
            </header>

            {/* P2：问答次数 / 检索耗时等仪表盘卡片待后端指标接口后再接 */}
            <section aria-label="概览指标" className="grid gap-4 sm:grid-cols-2">
                <Card className="border-indigo-100/80 bg-gradient-to-br from-indigo-50/80 to-white">
                    <CardContent className="flex items-center gap-4 p-5 sm:p-6">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-indigo-100 text-indigo-600">
                            <BookOpen className="h-6 w-6" aria-hidden />
                        </div>
                        <div className="min-w-0">
                            <p className="text-xs font-semibold uppercase tracking-wider text-indigo-600/90">入库小说</p>
                            <p className="mt-1 text-3xl font-bold tabular-nums text-indigo-950">
                                {isNovelsLoading ? (
                                    <Loader2 className="h-7 w-7 animate-spin text-indigo-400" aria-hidden />
                                ) : (
                                    novels?.length ?? 0
                                )}
                            </p>
                            <p className="mt-0.5 text-xs text-indigo-700/70">已入库版本数</p>
                        </div>
                    </CardContent>
                </Card>

                <Card className="border-emerald-100/80 bg-gradient-to-br from-emerald-50/80 to-white">
                    <CardContent className="flex items-center gap-4 p-5 sm:p-6">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-emerald-100 text-emerald-600">
                            <Database className="h-6 w-6" aria-hidden />
                        </div>
                        <div className="min-w-0">
                            <p className="text-xs font-semibold uppercase tracking-wider text-emerald-600/90">向量总数</p>
                            <p className="mt-1 text-3xl font-bold tabular-nums text-emerald-950">
                                {isVectorStatsLoading ? (
                                    <Loader2 className="h-7 w-7 animate-spin text-emerald-400" aria-hidden />
                                ) : (
                                    vectorStats?.count ?? 0
                                )}
                            </p>
                            <p className="mt-0.5 text-xs text-emerald-700/70">ChromaDB 全量</p>
                        </div>
                    </CardContent>
                </Card>
            </section>

            <div className="grid min-w-0 gap-8 lg:grid-cols-12 lg:items-start">
                <section className="min-w-0 space-y-4 lg:col-span-5" aria-label="近期入库任务">
                    <div className="flex items-center justify-between gap-3">
                        <h2 className="text-lg font-semibold text-gray-900">近期入库任务</h2>
                        <span className="shrink-0 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-medium text-emerald-800">
                            {running} 运行中
                        </span>
                    </div>

                    <div className="space-y-3">
                        {isTasksLoading ? (
                            <Card className="border-dashed border-gray-200">
                                <CardContent className="flex justify-center py-12 text-gray-400">
                                    <Loader2 className="h-8 w-8 animate-spin" aria-hidden />
                                </CardContent>
                            </Card>
                        ) : recentTasks.length === 0 ? (
                            <Card className="border-dashed border-gray-200 bg-gray-50/30">
                                <CardContent className="py-10 text-center text-sm text-gray-500">暂无任务</CardContent>
                            </Card>
                        ) : (
                            recentTasks.map((task) => {
                                const displayTitle =
                                    task.novelTitle ?? titleById.get(task.novelId) ?? task.novelId;
                                return (
                                    <Card
                                        key={task.taskId}
                                        className="overflow-hidden border-gray-100 shadow-sm transition hover:border-gray-200 hover:shadow-md"
                                    >
                                        <CardContent className="space-y-3 p-4 sm:p-5">
                                            <div className="flex items-start gap-3">
                                                <div className="min-w-0 flex-1 space-y-1">
                                                    <p
                                                        className="truncate font-medium text-gray-900"
                                                        title={displayTitle}
                                                    >
                                                        {displayTitle}
                                                    </p>
                                                    <p
                                                        className="truncate font-mono text-xs text-gray-500"
                                                        title={task.novelId}
                                                    >
                                                        {task.novelId}
                                                    </p>
                                                    <p className="font-mono text-xs text-gray-400">{task.version}</p>
                                                </div>
                                                <div
                                                    className="flex shrink-0 pt-0.5"
                                                    title={task.status}
                                                    aria-label={task.status}
                                                >
                                                    <TaskStatusIcon status={task.status} />
                                                </div>
                                            </div>
                                            <div className="flex flex-wrap items-center gap-2 sm:flex-nowrap">
                                                <span
                                                    className={cn(
                                                        "shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium",
                                                        task.taskType === "EMBED"
                                                            ? "bg-indigo-100 text-indigo-800"
                                                            : "bg-sky-100 text-sky-800",
                                                    )}
                                                >
                                                    {taskPhaseLabel(task.taskType)}
                                                </span>
                                                <div className="flex min-w-[8rem] flex-1 items-center gap-2">
                                                    <div className="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-gray-100">
                                                        <div
                                                            className={cn(
                                                                "h-full rounded-full transition-[width]",
                                                                task.status === "FAILED"
                                                                    ? "bg-red-500"
                                                                    : "bg-blue-500",
                                                            )}
                                                            style={{ width: `${task.progress}%` }}
                                                        />
                                                    </div>
                                                    <span className="shrink-0 font-mono text-xs tabular-nums text-gray-500">
                                                        {task.progress}%
                                                    </span>
                                                </div>
                                            </div>
                                        </CardContent>
                                    </Card>
                                );
                            })
                        )}
                    </div>
                </section>

                <div className="flex min-w-0 flex-col gap-8 lg:col-span-7">
                    <Card className="min-w-0 overflow-hidden">
                        <CardHeader className="flex flex-row items-center gap-2 space-y-0 border-b border-gray-100 pb-4">
                            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet-50 text-violet-600">
                                <Activity className="h-4 w-4" aria-hidden />
                            </div>
                            <div className="min-w-0">
                                <CardTitle className="text-base font-semibold">运行与健康</CardTitle>
                                <p className="text-xs text-gray-500">Chroma 集合与本地推理依赖</p>
                            </div>
                        </CardHeader>
                        <CardContent className="grid gap-0 p-0 sm:grid-cols-2 sm:divide-x sm:divide-gray-100">
                            <div className="divide-y divide-gray-100">
                                <div className="flex items-start justify-between gap-3 px-5 py-4">
                                    <div className="min-w-0">
                                        <p className="text-sm font-medium text-gray-900">Collection</p>
                                        <p className="truncate text-xs text-gray-500" title="novel-splitter">
                                            novel-splitter
                                        </p>
                                    </div>
                                    <span className="shrink-0 rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-800">
                                        在线
                                    </span>
                                </div>
                                <div className="flex items-start justify-between gap-3 px-5 py-4">
                                    <div className="min-w-0">
                                        <p className="text-sm font-medium text-gray-900">总向量数</p>
                                        <p className="text-xs text-gray-500">所有版本合计</p>
                                    </div>
                                    <span className="shrink-0 font-mono text-sm font-semibold text-indigo-600 tabular-nums">
                                        {vectorStats?.count ?? "—"}
                                    </span>
                                </div>
                            </div>
                            <div className="divide-y divide-gray-100 border-t border-gray-100 sm:border-t-0">
                                <div className="flex items-start justify-between gap-3 px-5 py-4">
                                    <div className="flex min-w-0 items-center gap-2">
                                        <Cpu className="h-4 w-4 shrink-0 text-gray-400" aria-hidden />
                                        <div className="min-w-0">
                                            <p className="text-sm font-medium text-gray-900">Embedding</p>
                                            <p className="text-xs text-gray-500">本地 ONNX</p>
                                        </div>
                                    </div>
                                    {isModelHealthLoading ? (
                                        <Loader2 className="h-4 w-4 shrink-0 animate-spin text-gray-400" aria-hidden />
                                    ) : (
                                        <span
                                            className={cn(
                                                "shrink-0 rounded-full px-2 py-0.5 text-xs font-medium",
                                                modelHealth?.embeddingModelLoaded
                                                    ? "bg-emerald-100 text-emerald-800"
                                                    : "bg-red-100 text-red-800",
                                            )}
                                        >
                                            {modelHealth?.embeddingModelLoaded ? "已加载" : "未加载"}
                                        </span>
                                    )}
                                </div>
                                <div className="flex items-start justify-between gap-3 px-5 py-4">
                                    <div className="min-w-0">
                                        <p className="text-sm font-medium text-gray-900">LLM 后端</p>
                                        <p className="text-xs text-gray-500">API 可达性</p>
                                    </div>
                                    {isModelHealthLoading ? (
                                        <Loader2 className="h-4 w-4 shrink-0 animate-spin text-gray-400" aria-hidden />
                                    ) : (
                                        <span
                                            className={cn(
                                                "shrink-0 rounded-full px-2 py-0.5 text-xs font-medium",
                                                modelHealth?.llmBackendReachable
                                                    ? "bg-emerald-100 text-emerald-800"
                                                    : "bg-red-100 text-red-800",
                                            )}
                                        >
                                            {modelHealth?.llmBackendReachable ? "可达" : "不可达"}
                                        </span>
                                    )}
                                </div>
                            </div>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader className="border-b border-gray-100 pb-4">
                            <CardTitle className="text-base font-semibold">数据流向</CardTitle>
                            <p className="text-xs text-gray-500">入库与问答两条链路（组件名便于对照代码仓库）</p>
                        </CardHeader>
                        <CardContent className="space-y-8 p-5 sm:p-6">
                            <div className="space-y-3">
                                <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">入库</p>
                                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
                                    {INGEST_PIPELINE.map((s) => (
                                        <PipelineStep key={s.module} label={s.label} module={s.module} tone={s.tone} />
                                    ))}
                                </div>
                            </div>
                            <div className="space-y-3">
                                <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">问答</p>
                                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
                                    {QA_PIPELINE.map((s) => (
                                        <PipelineStep key={s.module} label={s.label} module={s.module} tone="emerald" />
                                    ))}
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                </div>
            </div>
        </div>
    );
}
