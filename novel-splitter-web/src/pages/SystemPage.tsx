import { useQuery } from '@tanstack/react-query';
import {
    Loader2,
    BookOpen,
    Database,
    Cpu,
    Activity,
    ExternalLink,
    CheckCircle2,
    XCircle,
    Clock,
} from "lucide-react";
import { vectorApi } from "@/api/vectorApi";
import { novelApi } from "@/api/novelApi";
import { taskApi } from "@/api/taskApi";
import type { SplitTask } from "@/api/taskApi";
import { cn } from "@/lib/utils";
import { useEffect, useState } from "react";

/* ────────── JetBrains Mono font ────────── */
const FONT_ID = "jetbrains-mono-font";
function useTerminalFont() {
    useEffect(() => {
        if (document.getElementById(FONT_ID)) return;
        const link = document.createElement("link");
        link.id = FONT_ID;
        link.rel = "stylesheet";
        link.href =
            "https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap";
        document.head.appendChild(link);
    }, []);
}

/* ────────── constants ────────── */
const RABBITMQ_CONSOLE = "http://localhost:15672";

/* ────────── helpers ────────── */
function taskPhaseLabel(t: SplitTask["taskType"]) {
    return t === "EMBED" ? "向量化" : "切分";
}

/* ────────── sub-components ────────── */

function StatusDot({ ok }: { ok: boolean }) {
    return (
        <span className="relative inline-flex h-2.5 w-2.5 shrink-0">
            <span
                className={cn(
                    "absolute inline-flex h-full w-full animate-ping rounded-full opacity-40",
                    ok ? "bg-emerald-400" : "bg-red-400",
                )}
            />
            <span
                className={cn(
                    "relative inline-flex h-2.5 w-2.5 rounded-full",
                    ok ? "bg-emerald-400 shadow-[0_0_10px_rgba(74,222,128,0.4)]" : "bg-red-400",
                )}
            />
        </span>
    );
}

function GlowingNumber({ value, accent }: { value: string | number; accent?: string }) {
    return (
        <span
            className="font-bold tabular-nums tracking-tight"
            style={{
                fontFamily: "'JetBrains Mono', monospace",
                color: accent ?? "#38bdf8",
                textShadow: accent ? `0 0 20px ${accent}40` : "0 0 20px rgba(34,211,238,0.25)",
            }}
        >
            {value}
        </span>
    );
}

/* ────────── main page ────────── */
export default function SystemPage() {
    useTerminalFont();

    const [uptime, setUptime] = useState<string>("--");
    useEffect(() => {
        const t0 = Date.now();
        const tick = () => {
            const s = Math.floor((Date.now() - t0) / 1000);
            const m = Math.floor(s / 60);
            const h = Math.floor(m / 60);
            setUptime(
                h > 0
                    ? `${h}h ${m % 60}m`
                    : m > 0
                        ? `${m}m ${s % 60}s`
                        : `${s}s`,
            );
        };
        tick();
        const id = setInterval(tick, 1000);
        return () => clearInterval(id);
    }, []);

    const { data: vectorStats } = useQuery({
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

    const INGEST_PIPELINE = [
        { label: "下载/读取", module: "novelDownloader" },
        { label: "切分", module: "splitter" },
        { label: "校验", module: "validation" },
        { label: "向量化", module: "embedding" },
        { label: "入库", module: "repository" },
    ];

    const QA_PIPELINE = [
        { label: "问题输入", module: "application" },
        { label: "向量化", module: "embedding" },
        { label: "召回", module: "retrieval" },
        { label: "组装", module: "context-assembler" },
        { label: "生成", module: "llm-client" },
    ];

    return (
        <div className="relative min-h-screen overflow-hidden bg-[#080c14] pb-12">
            {/* grid background */}
            <div
                className="pointer-events-none absolute inset-0 opacity-[0.03]"
                style={{
                    backgroundImage:
                        "linear-gradient(rgba(255,255,255,0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.05) 1px, transparent 1px)",
                    backgroundSize: "48px 48px",
                }}
            />

            <div className="relative mx-auto max-w-7xl px-4 pt-6 sm:px-6 lg:px-8">
                {/* ── header ── */}
                <div className="animate-[fadeIn_0.6s_ease-out]">
                    <div
                        className="mb-1 flex items-center gap-2 text-xs"
                        style={{ fontFamily: "'JetBrains Mono', monospace", color: "#8892b0" }}
                    >
                        <span className="inline-flex h-2 w-2 rounded-full bg-emerald-500 shadow-[0_0_8px_#10b981]" />
                        <span className="text-emerald-400">system@novel-splitter</span>
                        <span className="text-[#4b5563]">~</span>
                        <span className="text-[#4b5563]">$</span>
                        <span className="animate-pulse text-[#4b5563]">_</span>
                    </div>

                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                            <h1
                                className="text-3xl font-bold tracking-tight text-[#e2e8f0]"
                                style={{ fontFamily: "'JetBrains Mono', monospace" }}
                            >
                                System Dashboard
                            </h1>
                            <p className="mt-1 text-sm text-[#8892b0]">
                                Cluster status & pipeline monitoring terminal
                            </p>
                        </div>
                        <a
                            href={RABBITMQ_CONSOLE}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-[#334155] bg-[#111827] px-3 py-2 text-sm font-medium text-[#8892b0] transition hover:border-[#38bdf8]/50 hover:text-[#38bdf8]"
                        >
                            RabbitMQ Console
                            <ExternalLink className="h-3.5 w-3.5" aria-hidden />
                        </a>
                    </div>
                </div>

                {/* ── stat cards ── */}
                <div className="mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                    {[
                        {
                            label: "入库小说",
                            value: isNovelsLoading ? null : novels?.length ?? 0,
                            icon: BookOpen,
                            accent: "#38bdf8",
                            delay: 0,
                        },
                        {
                            label: "向量总数",
                            value: vectorStats?.count ?? null,
                            icon: Database,
                            accent: "#a78bfa",
                            delay: 0.1,
                        },
                        {
                            label: "运行中任务",
                            value: running,
                            icon: Activity,
                            accent: running > 0 ? "#f59e0b" : "#4ade80",
                            delay: 0.2,
                        },
                        {
                            label: "运行时间",
                            value: uptime,
                            icon: Clock,
                            accent: "#4ade80",
                            delay: 0.3,
                        },
                    ].map(({ label, value, icon: Icon, accent, delay }) => (
                        <div
                            key={label}
                            className="animate-[slideUp_0.5s_ease-out] rounded-xl border border-[#1e293b] bg-[#111827] p-5 opacity-0 [animation-fill-mode:forwards] hover:border-[#334155] hover:bg-[#1a2332]"
                            style={{ animationDelay: `${delay}s` }}
                        >
                            <div className="flex items-center gap-3">
                                <div
                                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg"
                                    style={{ backgroundColor: `${accent}15` }}
                                >
                                    <Icon className="h-5 w-5" style={{ color: accent }} />
                                </div>
                                <div className="min-w-0">
                                    <p className="text-xs font-medium uppercase tracking-wider text-[#8892b0]">
                                        {label}
                                    </p>
                                    {value !== null ? (
                                        <GlowingNumber value={value} accent={accent} />
                                    ) : (
                                        <Loader2
                                            className="mt-1 h-5 w-5 animate-spin"
                                            style={{ color: "#8892b0" }}
                                        />
                                    )}
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                {/* ── main grid ── */}
                <div className="mt-6 grid gap-6 lg:grid-cols-12 lg:items-start">
                    {/* ── left: recent tasks ── */}
                    <section
                        className="animate-[slideUp_0.5s_ease-out] lg:col-span-5"
                        style={{ animationDelay: "0.35s", animationFillMode: "forwards", opacity: 0 }}
                        aria-label="近期入库任务"
                    >
                        <div className="mb-3 flex items-center justify-between">
                            <h2
                                className="text-sm font-semibold uppercase tracking-widest text-[#8892b0]"
                                style={{ fontFamily: "'JetBrains Mono', monospace" }}
                            >
                                Recent Tasks
                            </h2>
                            <span
                                className={cn(
                                    "rounded-full border px-2.5 py-0.5 text-xs font-medium",
                                    running > 0
                                        ? "border-amber-500/40 bg-amber-500/10 text-amber-400"
                                        : "border-emerald-500/40 bg-emerald-500/10 text-emerald-400",
                                )}
                                style={{ fontFamily: "'JetBrains Mono', monospace" }}
                            >
                                {running} running
                            </span>
                        </div>

                        <div className="space-y-2">
                            {isTasksLoading ? (
                                <div className="flex justify-center rounded-lg border border-dashed border-[#1e293b] py-12">
                                    <Loader2 className="h-6 w-6 animate-spin text-[#4b5563]" />
                                </div>
                            ) : recentTasks.length === 0 ? (
                                <div className="rounded-lg border border-dashed border-[#1e293b] py-12 text-center text-sm text-[#4b5563]">
                                    No tasks yet
                                </div>
                            ) : (
                                recentTasks.map((task, idx) => {
                                    const displayTitle =
                                        task.novelTitle ?? titleById.get(task.novelId) ?? task.novelId;
                                    const isRunning = task.status === "PROCESSING";
                                    const isFailed = task.status === "FAILED";
                                    return (
                                        <div
                                            key={task.taskId}
                                            className="rounded-lg border border-[#1e293b] bg-[#111827] p-4 transition hover:border-[#334155] hover:bg-[#1a2332]"
                                            style={{
                                                animationDelay: `${0.4 + idx * 0.07}s`,
                                            }}
                                        >
                                            <div className="flex items-start gap-3">
                                                <div className="min-w-0 flex-1">
                                                    <div className="flex items-center gap-2">
                                                        <p
                                                            className="truncate text-sm font-medium text-[#e2e8f0]"
                                                            title={displayTitle}
                                                        >
                                                            {displayTitle}
                                                        </p>
                                                        {isRunning && (
                                                            <span className="inline-flex h-2 w-2 shrink-0">
                                                                <span className="absolute inline-flex h-2 w-2 animate-ping rounded-full bg-cyan-400 opacity-50" />
                                                                <span className="relative inline-flex h-2 w-2 rounded-full bg-cyan-400" />
                                                            </span>
                                                        )}
                                                    </div>
                                                    <p
                                                        className="mt-0.5 truncate text-xs"
                                                        style={{
                                                            fontFamily: "'JetBrains Mono', monospace",
                                                            color: "#4b5563",
                                                        }}
                                                        title={task.novelId}
                                                    >
                                                        {task.novelId.slice(0, 8)}…{task.version}
                                                    </p>
                                                </div>
                                                <span
                                                    className={cn(
                                                        "shrink-0 rounded-md px-2 py-0.5 text-[11px] font-medium",
                                                        task.taskType === "EMBED"
                                                            ? "bg-purple-500/10 text-purple-400"
                                                            : "bg-cyan-500/10 text-cyan-400",
                                                    )}
                                                    style={{ fontFamily: "'JetBrains Mono', monospace" }}
                                                >
                                                    {taskPhaseLabel(task.taskType)}
                                                </span>
                                            </div>

                                            {/* progress bar */}
                                            <div className="mt-3 flex items-center gap-2">
                                                <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-[#1e293b]">
                                                    <div
                                                        className={cn(
                                                            "h-full rounded-full transition-all duration-700",
                                                            isFailed
                                                                ? "bg-red-500"
                                                                : isRunning
                                                                    ? "bg-cyan-500"
                                                                    : "bg-emerald-500",
                                                        )}
                                                        style={{
                                                            width: `${task.progress}%`,
                                                            boxShadow:
                                                                isRunning && task.progress > 0
                                                                    ? "0 0 8px rgba(34,211,238,0.3)"
                                                                    : "none",
                                                        }}
                                                    />
                                                </div>
                                                <span
                                                    className="shrink-0 text-xs tabular-nums"
                                                    style={{
                                                        fontFamily: "'JetBrains Mono', monospace",
                                                        color: isFailed ? "#ef4444" : "#8892b0",
                                                    }}
                                                >
                                                    {task.progress}%
                                                </span>
                                            </div>

                                            {/* status row */}
                                            <div className="mt-2 flex items-center gap-1.5">
                                                {task.status === "SUCCESS" ? (
                                                    <>
                                                        <CheckCircle2 className="h-3 w-3 text-emerald-500" />
                                                        <span className="text-[11px] text-emerald-500/80">完成</span>
                                                    </>
                                                ) : task.status === "FAILED" ? (
                                                    <>
                                                        <XCircle className="h-3 w-3 text-red-500" />
                                                        <span className="text-[11px] text-red-400/80">失败</span>
                                                    </>
                                                ) : task.status === "PROCESSING" ? (
                                                    <>
                                                        <Loader2 className="h-3 w-3 animate-spin text-cyan-400" />
                                                        <span className="text-[11px] text-cyan-400/80">处理中</span>
                                                    </>
                                                ) : (
                                                    <>
                                                        <Clock className="h-3 w-3 text-[#4b5563]" />
                                                        <span className="text-[11px] text-[#4b5563]">等待</span>
                                                    </>
                                                )}
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </section>

                    {/* ── right column ── */}
                    <div
                        className="flex animate-[slideUp_0.5s_ease-out] flex-col gap-6 lg:col-span-7"
                        style={{ animationDelay: "0.45s", animationFillMode: "forwards", opacity: 0 }}
                    >
                        {/* health & status */}
                        <div className="rounded-lg border border-[#1e293b] bg-[#111827]">
                            <div className="flex items-center gap-2 border-b border-[#1e293b] px-5 py-3.5">
                                <Activity className="h-4 w-4 text-[#8892b0]" />
                                <h2
                                    className="text-xs font-semibold uppercase tracking-widest text-[#8892b0]"
                                    style={{ fontFamily: "'JetBrains Mono', monospace" }}
                                >
                                    Health & Status
                                </h2>
                            </div>
                            <div className="grid divide-y divide-[#1e293b] sm:grid-cols-2 sm:divide-x sm:divide-y-0">
                                {/* left side */}
                                <div className="divide-y divide-[#1e293b]">
                                    <div className="flex items-center justify-between px-5 py-4">
                                        <div>
                                            <p className="text-sm font-medium text-[#e2e8f0]">Chroma Collection</p>
                                            <p
                                                className="mt-0.5 text-xs"
                                                style={{
                                                    fontFamily: "'JetBrains Mono', monospace",
                                                    color: "#4b5563",
                                                }}
                                            >
                                                novel-splitter
                                            </p>
                                        </div>
                                        <StatusDot ok />
                                    </div>
                                    <div className="flex items-center justify-between px-5 py-4">
                                        <div>
                                            <p className="text-sm font-medium text-[#e2e8f0]">Total Vectors</p>
                                            <p className="mt-0.5 text-xs text-[#4b5563]">all versions</p>
                                        </div>
                                        <GlowingNumber value={vectorStats?.count ?? "—"} accent="#a78bfa" />
                                    </div>
                                </div>
                                {/* right side */}
                                <div className="divide-y divide-[#1e293b]">
                                    <div className="flex items-center justify-between px-5 py-4">
                                        <div className="flex items-center gap-2">
                                            <Cpu className="h-4 w-4 shrink-0 text-[#4b5563]" />
                                            <div>
                                                <p className="text-sm font-medium text-[#e2e8f0]">Embedding</p>
                                                <p className="text-xs text-[#4b5563]">local ONNX</p>
                                            </div>
                                        </div>
                                        {isModelHealthLoading ? (
                                            <Loader2 className="h-4 w-4 animate-spin text-[#4b5563]" />
                                        ) : (
                                            <StatusDot ok={!!modelHealth?.embeddingModelLoaded} />
                                        )}
                                    </div>
                                    <div className="flex items-center justify-between px-5 py-4">
                                        <div>
                                            <p className="text-sm font-medium text-[#e2e8f0]">LLM Backend</p>
                                            <p className="text-xs text-[#4b5563]">API reachability</p>
                                        </div>
                                        {isModelHealthLoading ? (
                                            <Loader2 className="h-4 w-4 animate-spin text-[#4b5563]" />
                                        ) : (
                                            <StatusDot ok={!!modelHealth?.llmBackendReachable} />
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* pipeline */}
                        <div className="rounded-lg border border-[#1e293b] bg-[#111827] p-5">
                            <h2
                                className="mb-1 text-xs font-semibold uppercase tracking-widest text-[#8892b0]"
                                style={{ fontFamily: "'JetBrains Mono', monospace" }}
                            >
                                Pipeline
                            </h2>
                            <p className="mb-5 text-xs text-[#4b5563]">
                                ingestion → qa — modules map to source tree
                            </p>

                            <div className="space-y-6">
                                <div>
                                    <p className="mb-2 text-[11px] font-medium uppercase tracking-wider text-[#4b5563]">
                                        Ingestion
                                    </p>
                                    <div className="grid grid-cols-5 gap-2">
                                        {INGEST_PIPELINE.map((s, i) => (
                                            <PipelineStep
                                                key={s.module}
                                                label={s.label}
                                                module={s.module}
                                                index={i}
                                                total={INGEST_PIPELINE.length}
                                            />
                                        ))}
                                    </div>
                                </div>
                                <div>
                                    <p className="mb-2 text-[11px] font-medium uppercase tracking-wider text-[#4b5563]">
                                        Q&A
                                    </p>
                                    <div className="grid grid-cols-5 gap-2">
                                        {QA_PIPELINE.map((s, i) => (
                                            <PipelineStep
                                                key={s.module}
                                                label={s.label}
                                                module={s.module}
                                                index={i}
                                                total={QA_PIPELINE.length}
                                            />
                                        ))}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

/* ────────── pipeline step block ────────── */
function PipelineStep({
    label,
    module,
    index,
    total,
}: {
    label: string;
    module: string;
    index: number;
    total: number;
}) {
    const hue = 190 + (index / total) * 40;
    const borderColor = `hsla(${hue}, 40%, 45%, 0.3)`;
    const textColor = `hsla(${hue}, 50%, 70%, 1)`;

    return (
        <div
            className="flex min-h-[3.75rem] flex-col justify-center rounded-lg border px-2 py-2 text-center transition hover:brightness-125"
            style={{
                borderColor,
                backgroundColor: `hsla(${hue}, 30%, 20%, 0.25)`,
            }}
        >
            <span
                className="text-xs font-semibold leading-tight"
                style={{ color: textColor, fontFamily: "'JetBrains Mono', monospace" }}
            >
                {label}
            </span>
            <span
                className="mt-1 truncate text-[10px] leading-none"
                style={{ color: `hsla(${hue}, 20%, 50%, 0.7)`, fontFamily: "'JetBrains Mono', monospace" }}
                title={module}
            >
                {module}
            </span>
        </div>
    );
}
