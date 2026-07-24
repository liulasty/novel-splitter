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
    Copy, BugPlay, Search, Layers, Terminal, Database, ChevronRight,
    Loader2, CheckCircle2, XCircle, Settings2,
} from 'lucide-react';
import type { NovelSummaryDto } from '@/api/novelApi';

/* ── JetBrains Mono font injection ── */
const FONT_ID = "rag-debug-mono";
function useMonoFont() {
    useEffect(() => {
        if (document.getElementById(FONT_ID)) return;
        const link = document.createElement("link");
        link.id = FONT_ID;
        link.rel = "stylesheet";
        link.href = "https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;600;700&display=swap";
        document.head.appendChild(link);
    }, []);
}
const mono = { fontFamily: "'JetBrains Mono', monospace" };

/* ── Tabs ── */
const TABS = [
    { id: 'retrieval', label: '检索结果', icon: Search, badge: (r: RagDebugResponse | null) => r?.retrievedScenes.length },
    { id: 'context', label: '上下文', icon: Layers, badge: (r: RagDebugResponse | null) => r?.contextBlocks.length },
    { id: 'prompt', label: '提示词', icon: Terminal, badge: null },
] as const;

/* ── Utils ── */
async function copy(text: string, msg = '已复制') {
    if (!text) return;
    try { await navigator.clipboard.writeText(text); toast.success(msg); }
    catch { toast.error('复制失败'); }
}

/* ── JSON highlighting ── */
function highlightJson(raw: string): { __html: string } {
    const esc = raw.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    const hl = esc
        .replace(/(?<!")(\btrue\b|\bfalse\b)(?!")/g, '<span class="json-bool">$1</span>')
        .replace(/(?<!")\bnull\b(?!")/g, '<span class="json-null">$1</span>')
        .replace(/"(-?\d+\.?\d*(?:[eE][+-]?\d+)?)"/g, '"<span class="json-num">$1</span>"')
        .replace(/(?<!")(-?\d+\.?\d*)(?!")/g, '<span class="json-num">$1</span>')
        .replace(/"((?:[^"\\]|\\.)*)"\s*:/g, '<span class="json-key">"$1"</span>:')
        .replace(/:\s*"((?:[^"\\]|\\.)*)"/g, ': <span class="json-str">"$1"</span>');
    return { __html: hl };
}

/* ══════════════════════════════════════════
   PAGE COMPONENT
   ══════════════════════════════════════════ */
export default function RagDebugPage() {
    useMonoFont();

    /* state */
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

    /* data */
    useEffect(() => {
        novelApi.getNovelSummaries('embed_ready')
            .then(list => setNovels(list.map(n => ({ novelId: n.novelId, title: n.title, status: n.status }))))
            .catch(console.error);
    }, []);
    useEffect(() => {
        if (selectedNovel) {
            knowledgeApi.listSplitProfilesByNovelId(selectedNovel)
                .then(list => { setSplitProfiles(list); setSelectedProfileIndex(list.length - 1); })
                .catch(console.error);
        } else { setSplitProfiles([]); setSelectedProfileIndex(0); }
    }, [selectedNovel]);

    /* debug action */
    const handleDebug = async () => {
        if (!question) return;
        setLoading(true); setError(null); setResult(null);
        setChromaCollection(null); setCollectionCount(null); setVersionRecordCount(null);
        try {
            const profile = splitProfiles[selectedProfileIndex];
            if (!profile?.version) { setError('请选择有效的数据集'); setLoading(false); return; }
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
                    } catch { /* optional */ }
                })()
            ]);
            setResult(data);
            setActiveTab('retrieval');
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : '调试请求失败');
        } finally { setLoading(false); }
    };

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
    const totalTokens = useMemo(() => result ? estimateTokens(generateFullPrompt()) : 0, [result]);

    /* ═══════ RENDER ═══════ */
    return (
        <div className="flex h-screen flex-col bg-[#F7F6F3]">
            {/* ── header bar ── */}
            <header className="flex-none border-b border-[#E2DDD4] bg-white px-5 py-3">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-[#D97706]/10">
                            <BugPlay className="h-4 w-4 text-[#D97706]" />
                        </div>
                        <div>
                            <h1 className="text-sm font-semibold text-[#1A1A1A]" style={mono}>RAG Debug</h1>
                            <p className="text-[11px] text-[#6B7280]" style={mono}>retrieval → context → prompt</p>
                        </div>
                        {loading && (
                            <span className="relative flex h-2.5 w-2.5">
                                <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-[#D97706] opacity-60" />
                                <span className="relative inline-flex h-2.5 w-2.5 rounded-full bg-[#D97706]" />
                            </span>
                        )}
                    </div>
                </div>
            </header>

            {/* ── body: three-column ── */}
            <div className="flex flex-1 overflow-hidden">
                {/* ── left: icon tabs ── */}
                <nav className="flex w-14 flex-none flex-col items-center gap-1 border-r border-[#E2DDD4] bg-white py-4">
                    {TABS.map(tab => {
                        const active = activeTab === tab.id;
                        return (
                            <button key={tab.id} type="button" onClick={() => result && setActiveTab(tab.id)}
                                className={cn(
                                    "flex h-10 w-10 items-center justify-center rounded-lg text-sm transition",
                                    active
                                        ? "bg-[#D97706] text-white shadow-sm"
                                        : "text-[#9CA3AF] hover:bg-[#F0EDE8] hover:text-[#6B7280]",
                                    !result && "cursor-not-allowed opacity-40"
                                )}
                                disabled={!result}
                                title={tab.label}
                            >
                                <tab.icon className="h-4 w-4" />
                            </button>
                        );
                    })}
                </nav>

                {/* ── middle: params panel ── */}
                <aside className="w-80 flex-none border-r border-[#E2DDD4] bg-white overflow-y-auto">
                    <div className="p-4">
                        <div className="mb-4 flex items-center gap-2">
                            <Settings2 className="h-3.5 w-3.5 text-[#9CA3AF]" />
                            <span className="text-[11px] font-medium uppercase tracking-wider text-[#6B7280]" style={mono}>Parameters</span>
                        </div>

                        <div className="space-y-3.5">
                            {/* Novel */}
                            <div>
                                <label className="mb-1 block text-xs font-medium text-[#6B7280]" style={mono}>Novel</label>
                                <SelectMenu value={selectedNovel} onValueChange={setSelectedNovel} options={novelOptions}
                                    placeholder="选择小说…" className="w-full" emptyMessage="暂无已向量化小说" />
                            </div>
                            {/* Dataset */}
                            <div>
                                <label className="mb-1 block text-xs font-medium text-[#6B7280]" style={mono}>Dataset</label>
                                <SelectMenu value={splitProfiles.length ? String(selectedProfileIndex) : ''}
                                    onValueChange={v => setSelectedProfileIndex(Number(v))} options={profileOptions}
                                    placeholder={selectedNovel ? '选择切片配置…' : '—'} disabled={!selectedNovel || !splitProfiles.length}
                                    className="w-full" emptyMessage="暂无切片配置" />
                            </div>
                            {/* Query */}
                            <div>
                                <label className="mb-1 block text-xs font-medium text-[#6B7280]" style={mono}>Query</label>
                                <input type="text" value={question} onChange={e => setQuestion(e.target.value)}
                                    onKeyDown={e => e.key === 'Enter' && handleDebug()}
                                    placeholder="输入检索问题…"
                                    className="w-full rounded-lg border border-[#E2DDD4] bg-[#F7F6F3] px-3 py-2 text-sm text-[#1A1A1A] placeholder:text-[#9CA3AF] transition focus:border-[#D97706]/50 focus:outline-none focus:ring-2 focus:ring-[#D97706]/15" />
                            </div>
                            {/* Params row */}
                            <div className="grid grid-cols-2 gap-2">
                                {[
                                    { label: 'TopK', value: topK, set: setTopK, min: 1, max: 50 },
                                    { label: 'Scenes', value: maxScenes, set: setMaxScenes, min: 1, max: 50 },
                                    { label: 'CTX Tok', value: maxContextTokens, set: setMaxContextTokens, min: 500, max: 16000 },
                                    { label: 'Ans Tok', value: maxAnswerTokens, set: setMaxAnswerTokens, min: 0, max: 4000 },
                                ].map(p => (
                                    <div key={p.label}>
                                        <label className="mb-1 block text-xs font-medium text-[#6B7280]" style={mono}>{p.label}</label>
                                        <input type="number" value={p.value} onChange={e => {
                                            const v = parseInt(e.target.value, 10);
                                            p.set(Number.isNaN(v) ? p.min : Math.min(p.max, Math.max(p.min, v)));
                                        }} min={p.min} max={p.max}
                                            className="w-full rounded-lg border border-[#E2DDD4] bg-[#F7F6F3] px-3 py-2 text-center text-sm text-[#1A1A1A] transition focus:border-[#D97706]/50 focus:outline-none focus:ring-2 focus:ring-[#D97706]/15 tabular-nums" />
                                    </div>
                                ))}
                            </div>
                            {/* Debug button */}
                            <button type="button" onClick={handleDebug} disabled={loading || !question}
                                className="flex w-full items-center justify-center gap-2 rounded-lg bg-[#D97706] px-4 py-2.5 text-sm font-medium text-white shadow-sm transition hover:bg-[#B85C00] disabled:cursor-not-allowed disabled:opacity-40">
                                {loading ? (
                                    <><Loader2 className="h-4 w-4 animate-spin" /> RUNNING</>
                                ) : (
                                    <><ChevronRight className="h-4 w-4" /> DEBUG</>
                                )}
                            </button>

                            {/* error */}
                            {error && (
                                <div role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">
                                    {error}
                                </div>
                            )}
                        </div>
                    </div>
                </aside>

                {/* ── right: main content ── */}
                <main className="flex flex-1 flex-col overflow-hidden bg-[#F7F6F3]">
                    {result ? (
                        <>
                            {/* stats bar */}
                            <div className="flex-none border-b border-[#E2DDD4] bg-white px-5 py-2.5">
                                <div className="flex flex-wrap items-center gap-5">
                                    {result.stats && Object.entries(result.stats).map(([k, v]) => (
                                        <div key={k} className="flex items-center gap-1.5">
                                            <span className="text-[11px] uppercase tracking-wider text-[#9CA3AF]" style={mono}>{k}</span>
                                            <span className="text-sm font-semibold text-[#D97706]" style={mono}>{String(v)}</span>
                                        </div>
                                    ))}
                                    <div className="h-4 w-px bg-[#E2DDD4]" />
                                    {chromaCollection && (
                                        <>
                                            <div className="flex items-center gap-1.5">
                                                <Database className="h-3.5 w-3.5 text-[#9CA3AF]" />
                                                <span className="text-[11px] text-[#9CA3AF]" style={mono}>{chromaCollection.name}</span>
                                                <span className={cn("text-xs font-semibold", collectionCount ? "text-[#D97706]" : "text-[#9CA3AF]")} style={mono}>
                                                    {collectionCount !== null ? `${collectionCount} vec` : '…'}
                                                </span>
                                            </div>
                                            {versionRecordCount !== null && (
                                                <div className="flex items-center gap-1">
                                                    {versionRecordCount > 0
                                                        ? <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                                                        : <XCircle className="h-3.5 w-3.5 text-red-500" />}
                                                    <span className="text-xs text-[#6B7280]" style={mono}>ver match</span>
                                                </div>
                                            )}
                                        </>
                                    )}
                                    <div className="ml-auto hidden items-center gap-1.5 sm:flex">
                                        <span className="text-[11px] text-[#9CA3AF]" style={mono}>~{totalTokens} tok</span>
                                    </div>
                                </div>
                            </div>

                            {/* content */}
                            <div className="flex-1 overflow-y-auto px-5 py-4">
                                {activeTab === 'retrieval' && <RetrievalTab result={result} />}
                                {activeTab === 'context' && <ContextTab result={result} />}
                                {activeTab === 'prompt' && <PromptTab result={result} prompt={generateFullPrompt()} totalTokens={totalTokens} />}
                            </div>
                        </>
                    ) : (
                        <div className="flex flex-1 flex-col items-center justify-center">
                            <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl border border-dashed border-[#D1D5DB] bg-white">
                                <Search className="h-6 w-6 text-[#9CA3AF]" />
                            </div>
                            <p className="text-base font-medium text-[#374151]" style={mono}>Awaiting query</p>
                            <p className="mt-2 text-sm text-[#9CA3AF]" style={mono}>
                                set parameters on the left, then run <span className="text-[#D97706] font-medium">DEBUG</span>
                            </p>
                        </div>
                    )}
                </main>
            </div>
        </div>
    );
}

/* ══════════════════════════════════════════
   TABS
   ══════════════════════════════════════════ */

function RetrievalTab({ result }: { result: RagDebugResponse }) {
    const scenes = result.retrievedScenes;
    return (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            {scenes.map((s, i) => (
                <CollapseCard key={s.id || i}
                    title={
                        <div className="flex items-center gap-2 min-w-0">
                            <span className="text-xs font-bold text-[#D97706] shrink-0" style={mono}>#{i + 1}</span>
                            <span className="truncate text-xs text-[#6B7280]" style={mono}>{s.id || `result-${i}`}</span>
                        </div>
                    }
                    extra={
                        <button onClick={() => copy(s.content)} className="rounded p-1 text-[#9CA3AF] transition hover:bg-[#F0EDE8] hover:text-[#6B7280]">
                            <Copy className="h-3.5 w-3.5" />
                        </button>
                    }
                    className="border border-[#E2DDD4] bg-white hover:border-[#D1D5DB]"
                >
                    <div className="mb-2 overflow-x-auto rounded-md bg-[#F0EDE8] p-2 text-xs json-highlight" style={mono}
                        dangerouslySetInnerHTML={highlightJson(JSON.stringify(s.metadata, null, 1))} />
                    <p className="whitespace-pre-wrap text-sm leading-relaxed text-[#374151]">{s.content}</p>
                </CollapseCard>
            ))}
            {scenes.length === 0 && (
                <div className="col-span-full py-12 text-center text-sm text-[#9CA3AF]" style={mono}>no results</div>
            )}
        </div>
    );
}

function ContextTab({ result }: { result: RagDebugResponse }) {
    const blocks = result.contextBlocks;
    return (
        <div className="grid gap-3 sm:grid-cols-2">
            {blocks.map((b, i) => (
                <CollapseCard key={b.chunkId || i}
                    title={
                        <div className="flex items-center gap-3 min-w-0">
                            <span className="text-xs font-bold text-[#D97706] shrink-0" style={mono}>#{i + 1}</span>
                            <span className="truncate text-xs text-[#6B7280]" style={mono}>{b.chunkId}</span>
                            {(b.sceneMetadata as any)?.chapterTitle && (
                                <span className="shrink-0 rounded border border-[#D97706]/30 bg-[#D97706]/10 px-1.5 py-0.5 text-[11px] font-medium text-[#D97706]" style={mono}>
                                    {(b.sceneMetadata as any).chapterTitle}
                                </span>
                            )}
                            <span className="shrink-0 rounded border border-[#D1D5DB] bg-[#F0EDE8] px-1.5 py-0.5 text-[11px] text-[#6B7280]" style={mono}>
                                {b.tokenCount} tok
                            </span>
                            <span className="shrink-0 rounded border border-[#E2DDD4] bg-[#FDFCFB] px-1.5 py-0.5 text-[11px] text-[#D97706]" style={mono}>
                                {b.score.toFixed(4)}
                            </span>
                        </div>
                    }
                    extra={
                        <button onClick={() => copy(b.content)} className="rounded p-1 text-[#9CA3AF] transition hover:bg-[#F0EDE8] hover:text-[#6B7280]">
                            <Copy className="h-3.5 w-3.5" />
                        </button>
                    }
                    className="border border-[#E2DDD4] bg-white hover:border-[#D1D5DB]"
                >
                    <p className="whitespace-pre-wrap text-sm leading-relaxed text-[#374151]">{b.content}</p>
                </CollapseCard>
            ))}
            {blocks.length === 0 && (
                <div className="col-span-full py-12 text-center text-sm text-[#9CA3AF]" style={mono}>no context blocks</div>
            )}
        </div>
    );
}

function PromptTab({ result, prompt, totalTokens }: { result: RagDebugResponse; prompt: string; totalTokens: number }) {
    const fp = result.finalPrompt;
    return (
        <div className="space-y-4 max-w-4xl">
            {/* full prompt */}
            <div className="rounded-lg border border-[#E2DDD4] bg-white">
                <div className="flex items-center justify-between border-b border-[#E2DDD4] px-4 py-3">
                    <div className="flex items-center gap-3">
                        <h3 className="text-sm font-semibold text-[#1A1A1A]" style={mono}>Complete Prompt</h3>
                        <span className="rounded border border-[#E2DDD4] bg-[#F7F6F3] px-2 py-0.5 text-[11px] text-[#6B7280]" style={mono}>~{totalTokens} tok</span>
                    </div>
                    <button type="button" onClick={() => copy(prompt, 'Prompt copied')}
                        className="flex items-center gap-1.5 rounded-lg bg-[#D97706] px-3 py-1.5 text-xs font-medium text-white transition hover:bg-[#B85C00]" style={mono}>
                        <Copy className="h-3.5 w-3.5" /> Copy
                    </button>
                </div>
                <textarea readOnly value={prompt}
                    className="w-full resize-none border-0 bg-[#F7F6F3] p-4 text-sm text-[#374151] focus:outline-none"
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
                                <span className="rounded bg-[#F0EDE8] px-1.5 py-0.5 text-[11px] font-bold text-[#D97706]" style={mono}>{s.icon}</span>
                                <span className="text-xs font-medium text-[#1A1A1A]" style={mono}>{s.label}</span>
                            </div>
                        }
                        extra={
                            <button onClick={() => copy(s.content)} className="rounded p-1 text-[#9CA3AF] transition hover:bg-[#F0EDE8] hover:text-[#6B7280]">
                                <Copy className="h-3.5 w-3.5" />
                            </button>
                        }
                        defaultOpen={false}
                        className="border border-[#E2DDD4] bg-white hover:border-[#D1D5DB]"
                    >
                        <pre className="max-h-80 overflow-y-auto whitespace-pre-wrap rounded-md bg-[#F0EDE8] p-3 text-xs text-[#374151]" style={mono}>
                            {s.content}
                        </pre>
                    </CollapseCard>
                ))}
            </div>
        </div>
    );
}
