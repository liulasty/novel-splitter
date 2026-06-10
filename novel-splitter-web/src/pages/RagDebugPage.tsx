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
import {
    Copy, BugPlay, Search, Layers, Terminal, Database, Cpu,
    ChevronRight, Loader2, CheckCircle2, XCircle
} from 'lucide-react';
import type { NovelSummaryDto } from '@/api/novelApi';

/* ── JSON syntax highlighting ── */
function highlightJson(raw: string): { __html: string } {
    const escaped = raw
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const highlighted = escaped
        .replace(/(?<!")(\btrue\b|\bfalse\b)(?!")/g, '<span class="json-bool">$1</span>')
        .replace(/(?<!")\bnull\b(?!")/g, '<span class="json-null">$1</span>')
        .replace(/"(-?\d+\.?\d*(?:[eE][+-]?\d+)?)"/g, '"<span class="json-num">$1</span>"')
        .replace(/(?<!")(-?\d+\.?\d*)(?!")/g, '<span class="json-num">$1</span>')
        .replace(/"((?:[^"\\]|\\.)*)"\s*:/g, '<span class="json-key">"$1"</span>:')
        .replace(/:\s*"((?:[^"\\]|\\.)*)"/g, ': <span class="json-str">"$1"</span>');
    return { __html: highlighted };
}

/* ── font injection ── */
const FONT_ID = "rag-debug-font";
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

const mono = { fontFamily: "'JetBrains Mono', monospace" };

/* ── tabs ── */
const TABS = [
    { id: 'retrieval', label: '检索结果', icon: Search, badge: (r: RagDebugResponse | null) => r?.retrievedScenes.length },
    { id: 'context', label: '上下文组装', icon: Layers, badge: (r: RagDebugResponse | null) => r?.contextBlocks.length },
    { id: 'prompt', label: '最终提示词', icon: Terminal, badge: null },
] as const;

/* ── utils ── */
async function copy(text: string, msg = '已复制') {
    if (!text) return;
    try { await navigator.clipboard.writeText(text); toast.success(msg); }
    catch { toast.error('复制失败'); }
}

/* ══════════════════════════════════════════
   PAGE
   ══════════════════════════════════════════ */
export default function RagDebugPage() {
    useTerminalFont();

    /* ── state ── */
    const [novels, setNovels] = useState<Array<Pick<NovelSummaryDto, 'novelId' | 'title' | 'status'>>>([]);
    const [splitProfiles, setSplitProfiles] = useState<SceneSplitProfileDto[]>([]);
    const [selectedProfileIndex, setSelectedProfileIndex] = useState(0);
    const [selectedNovel, setSelectedNovel] = useState('');
    const [question, setQuestion] = useState('');
    const [topK, setTopK] = useState(5);
    const [maxScenes, setMaxScenes] = useState(5);
    const [maxContextTokens, setMaxContextTokens] = useState(3000);
    const [maxAnswerTokens, setMaxAnswerTokens] = useState(0);

    const [result, setResult] = useState<RagDebugResponse | null>(null);
    const [chromaCollection, setChromaCollection] = useState<ChromaCollection | null>(null);
    const [collectionCount, setCollectionCount] = useState<number | null>(null);
    const [versionRecordCount, setVersionRecordCount] = useState<number | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [activeTab, setActiveTab] = useState<string>('retrieval');

    /* ── data loading ── */
    useEffect(() => {
        novelApi.getNovelSummaries('embed_ready')
            .then((list) => setNovels(list.map(n => ({ novelId: n.novelId, title: n.title, status: n.status }))))
            .catch(console.error);
    }, []);

    useEffect(() => {
        if (selectedNovel) {
            knowledgeApi.listSplitProfilesByNovelId(selectedNovel)
                .then((list) => { setSplitProfiles(list); setSelectedProfileIndex(list.length - 1); })
                .catch(console.error);
        } else { setSplitProfiles([]); setSelectedProfileIndex(0); }
    }, [selectedNovel]);

    /* ── debug action ── */
    const handleDebug = async () => {
        if (!question) return;
        setLoading(true); setError(null); setResult(null);
        setChromaCollection(null); setCollectionCount(null); setVersionRecordCount(null);

        try {
            const profile = splitProfiles[selectedProfileIndex];
            if (!profile?.version) { setError('请选择有效的数据集（版本 / 滑窗）'); setLoading(false); return; }

            const request: ChatRequest = {
                question, novelId: selectedNovel, version: profile.version, topK,
                chunkSize: profile.chunkSize ?? undefined, chunkOverlap: profile.chunkOverlap ?? undefined,
                maxScenes, maxContextTokens,
                maxAnswerTokens: maxAnswerTokens > 0 ? maxAnswerTokens : undefined,
            };

            const [data] = await Promise.all([
                ragApi.debug(request),
                (async () => {
                    try {
                        const collections = await chromaAdminApi.getCollections();
                        const c = collections.find(x => x.name === 'novel-splitter') || collections[0];
                        if (!c) return;
                        setChromaCollection(c);
                        setCollectionCount(await chromaAdminApi.countDocuments(c.id));
                        const prof = splitProfiles[selectedProfileIndex];
                        if (selectedNovel && prof?.version) {
                            const andClauses: Record<string, { $eq: string | number }>[] = [
                                { novelId: { $eq: selectedNovel } }, { version: { $eq: prof.version } },
                            ];
                            if (prof.chunkSize != null && prof.chunkOverlap != null) {
                                andClauses.push({ chunkSize: { $eq: prof.chunkSize } });
                                andClauses.push({ chunkOverlap: { $eq: prof.chunkOverlap } });
                            }
                            const records = await chromaAdminApi.getRecords(c.id, {
                                where: { $and: andClauses }, limit: 1, include: ["metadatas", "documents"]
                            });
                            setVersionRecordCount(records?.ids?.length ?? 0);
                        }
                    } catch { /* diagnostics are optional */ }
                })()
            ]);

            setResult(data);
            setActiveTab('retrieval');

        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : '调试请求失败');
        } finally { setLoading(false); }
    };

    /* ── prompt assembly ── */
    const generateFullPrompt = () => {
        if (!result) return '';
        const { systemInstruction, contextBlocks, userQuestion, outputConstraint } = result.finalPrompt;
        const parts = [];
        if (systemInstruction) parts.push(`[System Instruction]\n${systemInstruction}`);
        if (contextBlocks?.length) {
            parts.push(`[Context]\n${contextBlocks.map((b, i) => {
                const title = (b.sceneMetadata as any)?.chapterTitle || b.metadata?.chapterTitle || '';
                return `--- Block ${i + 1}${title ? ` (${title})` : ''} [${b.chunkId}] ---\n${b.content}`;
            }).join('\n\n')}`);
        }
        parts.push(`[User Question]\n${userQuestion}`);
        if (outputConstraint) parts.push(`[Output Constraint]\n${outputConstraint}`);
        return parts.join('\n\n');
    };

    /* ── options ── */
    const novelOptions: SelectMenuOption[] = useMemo(
        () => novels.map(n => ({ value: n.novelId, label: n.title, description: n.status ? `状态: ${n.status}` : undefined })),
        [novels]
    );
    const profileOptions: SelectMenuOption[] = useMemo(
        () => splitProfiles.map((p, i) => ({
            value: String(i), label: p.version,
            description: p.chunkSize != null ? `chunk: ${p.chunkSize} / overlap: ${p.chunkOverlap}` : undefined,
            badge: p.chunkSize != null ? `${p.chunkSize}/${p.chunkOverlap}` : undefined,
        })),
        [splitProfiles]
    );

    const totalTokens = useMemo(() => result ? estimateTokens(generateFullPrompt()) : 0, [result, generateFullPrompt]);

    /* ═══════════ RENDER ═══════════ */
    return (
        <div className="relative flex h-screen flex-col overflow-hidden bg-[#080c14]">
            {/* grid bg */}
            <div className="pointer-events-none absolute inset-0 opacity-[0.025]"
                style={{ backgroundImage: "linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px)", backgroundSize: "48px 48px" }} />

            {/* ── header ── */}
            <header className="relative z-10 flex-none border-b border-[#1e293b] bg-[#080c14]/95 backdrop-blur-md">
                <div className="px-6 py-4">
                    {/* top row: title + status */}
                    <div className="mb-4 flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-cyan-500/15">
                                <BugPlay className="h-5 w-5 text-cyan-400" />
                            </div>
                            <div>
                                <h1 className="text-lg font-bold text-[#e2e8f0]" style={mono}>RAG Debug</h1>
                                <p className="text-xs text-[#8892b0]" style={mono}>retrieval → context → prompt</p>
                            </div>
                            {loading && (
                                <span className="relative flex h-3 w-3">
                                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-cyan-400 opacity-60" />
                                    <span className="relative inline-flex h-3 w-3 rounded-full bg-cyan-500" />
                                </span>
                            )}
                        </div>
                    </div>

                    {/* controls row */}
                    <div className="flex flex-wrap items-end gap-3">
                        {/* novel */}
                        <div className="space-y-1">
                            <span className="block text-[11px] font-medium uppercase tracking-wider text-[#8892b0]" style={mono}>Novel</span>
                            <SelectMenu value={selectedNovel} onValueChange={setSelectedNovel} options={novelOptions}
                                placeholder="选择小说…" className="w-44" emptyMessage="暂无已向量化小说" />
                        </div>
                        {/* profile */}
                        <div className="space-y-1">
                            <span className="block text-[11px] font-medium uppercase tracking-wider text-[#8892b0]" style={mono}>Dataset</span>
                            <SelectMenu value={splitProfiles.length ? String(selectedProfileIndex) : ''}
                                onValueChange={(v) => setSelectedProfileIndex(Number(v))} options={profileOptions}
                                placeholder={selectedNovel ? '选择切片配置…' : '—'} disabled={!selectedNovel || !splitProfiles.length}
                                className="w-44" emptyMessage="暂无切片配置" />
                        </div>
                        {/* question */}
                        <div className="min-w-0 flex-1 space-y-1">
                            <label className="block text-[11px] font-medium uppercase tracking-wider text-[#8892b0]" style={mono}>Query</label>
                            <input type="text" value={question} onChange={e => setQuestion(e.target.value)}
                                onKeyDown={e => e.key === 'Enter' && handleDebug()}
                                placeholder="输入检索问题…"
                                className="w-full rounded-lg border border-[#1e293b] bg-[#111827] px-3 py-2 text-sm text-[#e2e8f0] placeholder:text-[#4b5563] transition focus:border-cyan-500/50 focus:outline-none focus:ring-1 focus:ring-cyan-500/30"
                                style={mono} />
                        </div>
                        {/* params */}
                        {([{ label: 'TopK', key: topK, set: setTopK, min: 1, max: 50, def: 5 },
                            { label: 'Scenes', key: maxScenes, set: setMaxScenes, min: 1, max: 50, def: 5 },
                            { label: 'CTX Tok', key: maxContextTokens, set: setMaxContextTokens, min: 500, max: 16000, def: 3000 },
                            { label: 'Ans Tok', key: maxAnswerTokens, set: setMaxAnswerTokens, min: 0, max: 4000, def: 0 },
                        ] as const).map(p => (
                            <div key={p.label} className="space-y-1">
                                <span className="block text-[11px] font-medium uppercase tracking-wider text-[#8892b0]" style={mono}>{p.label}</span>
                                <input type="number" value={p.key} onChange={e => {
                                    const v = parseInt(e.target.value, 10);
                                    p.set(Number.isNaN(v) ? p.def : Math.min(p.max, Math.max(p.min, v)));
                                }} min={p.min} max={p.max}
                                    className="w-20 rounded-lg border border-[#1e293b] bg-[#111827] px-2 py-2 text-center text-sm text-[#e2e8f0] transition focus:border-cyan-500/50 focus:outline-none focus:ring-1 focus:ring-cyan-500/30 tabular-nums"
                                    style={mono} />
                            </div>
                        ))}
                        {/* debug button */}
                        <button type="button" onClick={handleDebug} disabled={loading || !question}
                            className="inline-flex h-[38px] items-center gap-2 rounded-lg bg-cyan-600 px-5 text-sm font-medium text-white shadow-sm transition hover:bg-cyan-500 disabled:cursor-not-allowed disabled:opacity-40"
                            style={mono}>
                            {loading ? 'RUNNING…' : 'DEBUG →'}
                        </button>
                    </div>

                    {/* error */}
                    {error && (
                        <div role="alert" className="mt-3 rounded-lg border border-red-500/30 bg-red-500/10 px-3 py-2 text-sm text-red-400" style={mono}>
                            {error}
                        </div>
                    )}
                </div>
            </header>

            {/* ── body ── */}
            <div className="relative flex flex-1 flex-col overflow-hidden">
                {result ? (
                    <>
                        {/* ── diag + stats bar ── */}
                        <div className="flex-none border-b border-[#1e293b] bg-[#111827]/80 px-6 py-3">
                            <div className="flex flex-wrap items-center gap-6">
                                {/* stats */}
                                {result.stats && Object.entries(result.stats).map(([k, v]) => (
                                    <div key={k} className="flex items-center gap-2">
                                        <span className="text-[11px] uppercase tracking-wider text-[#4b5563]" style={mono}>{k}</span>
                                        <span className="text-sm font-semibold text-[#38bdf8]" style={mono}>{String(v)}</span>
                                    </div>
                                ))}
                                <div className="h-4 w-px bg-[#1e293b]" />
                                {/* Chroma info */}
                                {chromaCollection && (
                                    <>
                                        <div className="flex items-center gap-2">
                                            <Database className="h-3.5 w-3.5 text-[#4b5563]" />
                                            <span className="text-[11px] text-[#4b5563]" style={mono}>{chromaCollection.name}</span>
                                            <span className={cn("text-xs font-semibold", collectionCount ? "text-[#34d399]" : "text-[#4b5563]")} style={mono}>
                                                {collectionCount !== null ? `${collectionCount} vec` : '…'}
                                            </span>
                                        </div>
                                        {versionRecordCount !== null && (
                                            <div className="flex items-center gap-1.5">
                                                {versionRecordCount > 0
                                                    ? <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                                                    : <XCircle className="h-3.5 w-3.5 text-red-500" />}
                                                <span className="text-xs text-[#8892b0]" style={mono}>version match</span>
                                            </div>
                                        )}
                                    </>
                                )}
                                <div className="ml-auto hidden items-center gap-2 sm:flex">
                                    <Cpu className="h-3.5 w-3.5 text-[#4b5563]" />
                                    <span className="text-[11px] text-[#4b5563]" style={mono}>~{totalTokens} tokens</span>
                                </div>
                            </div>
                        </div>

                        {/* ── tabs ── */}
                        <div className="flex-none border-b border-[#1e293b] bg-[#080c14]/60 px-6">
                            <div className="flex gap-1">
                                {TABS.map(tab => {
                                    const active = activeTab === tab.id;
                                    const count = tab.badge?.(result);
                                    return (
                                        <button key={tab.id} type="button" onClick={() => setActiveTab(tab.id)}
                                            className={cn(
                                                "flex items-center gap-2 border-b-2 px-4 py-3 text-sm font-medium transition",
                                                active
                                                    ? "border-cyan-400 text-[#e2e8f0]"
                                                    : "border-transparent text-[#4b5563] hover:text-[#8892b0]",
                                            )}
                                            style={mono}>
                                            <tab.icon className="h-4 w-4" />
                                            {tab.label}
                                            {count != null && (
                                                <span className={cn("rounded-md px-1.5 py-0.5 text-[11px] font-medium",
                                                    active ? "bg-cyan-500/15 text-cyan-400" : "bg-[#1e293b] text-[#4b5563]")}>
                                                    {count}
                                                </span>
                                            )}
                                        </button>
                                    );
                                })}
                            </div>
                        </div>

                        {/* ── tab content ── */}
                        <div className="flex-1 overflow-y-auto px-6 py-5">
                            {activeTab === 'retrieval' && <RetrievalTab result={result} />}
                            {activeTab === 'context' && <ContextTab result={result} />}
                            {activeTab === 'prompt' && <PromptTab result={result} prompt={generateFullPrompt()} totalTokens={totalTokens} />}
                        </div>
                    </>
                ) : (
                    /* ── empty state ── */
                    <div className="flex flex-1 flex-col items-center justify-center px-6">
                        <div className="mb-6 flex h-20 w-20 items-center justify-center rounded-2xl border border-dashed border-[#1e293b] bg-[#111827]/50">
                            <Search className="h-8 w-8 text-[#475569]" />
                        </div>
                        <p className="text-lg font-medium text-[#8892b0]" style={mono}>Awaiting query</p>
                        <p className="mt-2 text-sm text-[#4b5563]" style={mono}>
                            select novel & dataset, enter a question, hit <span className="text-cyan-500">DEBUG</span>
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}

/* ══════════════════════════════════════════
   TAB: Retrieval
   ══════════════════════════════════════════ */
function RetrievalTab({ result }: { result: RagDebugResponse }) {
    const scenes = result.retrievedScenes;
    return (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {scenes.map((s, i) => (
                <CollapseCard key={s.id || i}
                    title={
                        <div className="flex items-center gap-2 min-w-0">
                            <span className="text-xs font-bold text-cyan-400 shrink-0" style={mono}>#{i + 1}</span>
                            <span className="truncate text-xs text-[#8892b0]" style={mono}>{s.id || `result-${i}`}</span>
                        </div>
                    }
                    extra={
                        <button onClick={() => copy(s.content)} className="rounded p-1 text-[#4b5563] transition hover:bg-[#1e293b] hover:text-[#8892b0]">
                            <Copy className="h-3.5 w-3.5" />
                        </button>
                    }
                    className="border border-[#1e293b] bg-[#111827] hover:border-[#475569]"
                >
                    <div className="mb-2 overflow-x-auto rounded-md bg-[#060a12] p-2 text-xs json-highlight" style={mono}
                        dangerouslySetInnerHTML={highlightJson(JSON.stringify(s.metadata, null, 1))} />
                    <p className="whitespace-pre-wrap text-sm leading-relaxed text-[#d4d4d8]">{s.content}</p>
                </CollapseCard>
            ))}
            {scenes.length === 0 && (
                <div className="col-span-full py-12 text-center text-sm text-[#4b5563]" style={mono}>no results</div>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════
   TAB: Context
   ══════════════════════════════════════════ */
function ContextTab({ result }: { result: RagDebugResponse }) {
    const blocks = result.contextBlocks;
    return (
        <div className="grid gap-3 sm:grid-cols-2">
            {blocks.map((b, i) => (
                <CollapseCard key={b.chunkId || i}
                    title={
                        <div className="flex items-center gap-3 min-w-0">
                            <span className="text-xs font-bold text-emerald-400 shrink-0" style={mono}>#{i + 1}</span>
                            <span className="truncate text-xs text-[#8892b0]" style={mono}>{b.chunkId}</span>
                            <span className="shrink-0 rounded border border-emerald-500/20 bg-emerald-500/10 px-1.5 py-0.5 text-[11px] text-emerald-400" style={mono}>
                                {b.tokenCount} tok
                            </span>
                            <span className="shrink-0 rounded border border-cyan-500/20 bg-cyan-500/10 px-1.5 py-0.5 text-[11px] text-cyan-400" style={mono}>
                                {b.score.toFixed(4)}
                            </span>
                        </div>
                    }
                    extra={
                        <button onClick={() => copy(b.content)} className="rounded p-1 text-[#4b5563] transition hover:bg-[#1e293b] hover:text-[#8892b0]">
                            <Copy className="h-3.5 w-3.5" />
                        </button>
                    }
                    className="border border-[#1e293b] bg-[#111827] hover:border-[#475569]"
                >
                    <p className="whitespace-pre-wrap text-sm leading-relaxed text-[#d4d4d8]">{b.content}</p>
                </CollapseCard>
            ))}
            {blocks.length === 0 && (
                <div className="col-span-full py-12 text-center text-sm text-[#4b5563]" style={mono}>no context blocks</div>
            )}
        </div>
    );
}

/* ══════════════════════════════════════════
   TAB: Prompt
   ══════════════════════════════════════════ */
function PromptTab({ result, prompt, totalTokens }: { result: RagDebugResponse; prompt: string; totalTokens: number }) {
    const fp = result.finalPrompt;
    return (
        <div className="space-y-5">
            {/* full prompt */}
            <div className="rounded-lg border border-[#1e293b] bg-[#111827]">
                <div className="flex items-center justify-between border-b border-[#1e293b] px-4 py-3">
                    <div className="flex items-center gap-3">
                        <h3 className="text-sm font-semibold text-[#e2e8f0]" style={mono}>Complete Prompt</h3>
                        <span className="rounded border border-[#475569] px-2 py-0.5 text-[11px] text-[#8892b0]" style={mono}>~{totalTokens} tok</span>
                    </div>
                    <button type="button" onClick={() => copy(prompt, 'Prompt copied')}
                        className="flex items-center gap-1.5 rounded-lg bg-cyan-600 px-3 py-1.5 text-xs font-medium text-white transition hover:bg-cyan-500" style={mono}>
                        <Copy className="h-3.5 w-3.5" /> Copy
                    </button>
                </div>
                <textarea readOnly value={prompt}
                    className="w-full resize-none border-0 bg-transparent p-4 text-sm text-[#d4d4d8] focus:outline-none"
                    style={mono} rows={20} onClick={e => (e.target as HTMLTextAreaElement).select()} />
            </div>

            {/* breakdown */}
            <div className="grid gap-4 lg:grid-cols-2">
                {[
                    { label: 'System Instruction', content: fp.systemInstruction, icon: 'SYS' },
                    { label: 'User Question', content: fp.userQuestion, icon: 'Q' },
                    { label: 'Output Constraint', content: fp.outputConstraint, icon: 'OUT' },
                    { label: 'Assembled Context', content: fp.contextBlocks.map(b => b.content).join('\n\n'), icon: 'CTX' },
                ].map(s => (
                    <CollapseCard key={s.label}
                        title={
                            <div className="flex items-center gap-2">
                                <span className="rounded bg-[#1e293b] px-1.5 py-0.5 text-[11px] font-bold text-cyan-400" style={mono}>{s.icon}</span>
                                <span className="text-xs font-medium text-[#e2e8f0]" style={mono}>{s.label}</span>
                            </div>
                        }
                        extra={
                            <button onClick={() => copy(s.content)} className="rounded p-1 text-[#4b5563] transition hover:bg-[#1e293b] hover:text-[#8892b0]">
                                <Copy className="h-3.5 w-3.5" />
                            </button>
                        }
                        defaultOpen={false}
                        className="border border-[#1e293b] bg-[#111827] hover:border-[#475569]"
                    >
                        <pre className="max-h-80 overflow-y-auto whitespace-pre-wrap rounded-md bg-[#080c14] p-3 text-xs text-[#8892b0]" style={mono}>
                            {s.content}
                        </pre>
                    </CollapseCard>
                ))}
            </div>
        </div>
    );
}
