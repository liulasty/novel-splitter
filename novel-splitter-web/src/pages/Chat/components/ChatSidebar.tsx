import { SelectMenu, type SelectMenuOption } from '@/components/ui/select-menu';
import type { NovelSummaryDto } from '@/api/novelApi';
import type { SceneSplitProfileDto } from '@/api/knowledgeApi';

/** 参数说明：(?) 圆圈，悬浮显示说明 */
function ParamHint({ text }: { text: string }) {
    return (
        <span className="relative inline-flex group">
            <span className="w-3.5 h-3.5 rounded-full bg-gray-300 text-white text-[10px] font-bold flex items-center justify-center leading-none cursor-help">
                ?
            </span>
            <span className="absolute left-0 top-full mt-1.5 z-30 hidden group-hover:block w-56 px-2.5 py-1.5 rounded-lg bg-gray-900 text-white text-[11px] leading-relaxed shadow-lg">
                {text}
            </span>
        </span>
    );
}

interface ChatSidebarProps {
    state: {
        novels: Array<Pick<NovelSummaryDto, 'novelId' | 'title' | 'status'>> | undefined;
        splitProfiles: SceneSplitProfileDto[];
        profileOptions: { value: string; label: string }[];
        selectedNovel: string;
        selectedVersion: string;
        topK: number;
        maxScenes: number;
        maxContextTokens: number;
        maxAnswerTokens: number;
    };
    actions: {
        setSelectedNovel: (val: string) => void;
        setSelectedVersion: (val: string) => void;
        setTopK: (val: number) => void;
        setMaxScenes: (val: number) => void;
        setMaxContextTokens: (val: number) => void;
        setMaxAnswerTokens: (val: number) => void;
    };
}

export function ChatSidebar({ state, actions }: ChatSidebarProps) {
    const novelOptions: SelectMenuOption[] = (state.novels ?? []).map(n => ({
        value: n.novelId,
        label: n.title,
        description: n.status ? `状态: ${n.status}` : undefined,
    }));

    const profileOptions: SelectMenuOption[] = state.splitProfiles.map((p) => ({
        value: p.version,
        label: p.version,
        description: p.chunkSize != null && p.chunkOverlap != null
            ? `chunk: ${p.chunkSize} / overlap: ${p.chunkOverlap}`
            : undefined,
        badge: p.chunkSize != null && p.chunkOverlap != null
            ? `${p.chunkSize}/${p.chunkOverlap}`
            : undefined,
    }));

    return (
        <div className="flex flex-col gap-3">
            <div className="rounded-2xl border border-gray-100 overflow-hidden shadow-sm bg-white">
                {/* Card gradient header */}
                <div className="px-4 py-3 bg-gradient-to-br from-violet-50 to-blue-50 border-b border-gray-100">
                    <h3 className="text-sm font-semibold text-violet-800">会话设置</h3>
                    <p className="text-xs text-violet-500 mt-0.5">选择小说版本与检索参数</p>
                </div>
                <div className="p-4 space-y-4">
                    {/* Novel select */}
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">选择小说</label>
                        <SelectMenu
                            value={state.selectedNovel}
                            onValueChange={actions.setSelectedNovel}
                            options={novelOptions}
                            placeholder="-- 请选择 --"
                            emptyMessage={novelOptions.length ? '暂无可选' : '加载中或暂无书目'}
                        />
                    </div>

                    {/* Split profile */}
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">数据集（版本 / 滑窗）</label>
                        <SelectMenu
                            value={state.selectedVersion}
                            onValueChange={actions.setSelectedVersion}
                            options={profileOptions}
                            placeholder="-- 请选择 --"
                            disabled={!state.selectedNovel || !profileOptions.length}
                            emptyMessage="该书暂无切片配置"
                        />
                    </div>

                    {/* TopK */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-1">
                                <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">引用数量 TopK</label>
                                <ParamHint text="向量检索返回的候选片段数，仅决定召回范围。最终进入上下文的场景还受『上下文场景上限』与『Token 预算』约束。" />
                            </div>
                            <span className="w-6 h-6 rounded-full bg-gradient-to-br from-violet-500 to-blue-500 text-white text-xs flex items-center justify-center font-semibold">
                                {state.topK}
                            </span>
                        </div>
                        <input
                            type="range" min={1} max={30} value={state.topK}
                            onChange={(e) => actions.setTopK(Number(e.target.value))}
                            className="w-full accent-violet-500"
                        />
                    </div>

                    {/* MaxScenes */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-1">
                                <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">上下文场景上限</label>
                                <ParamHint text="最终进入 LLM 上下文的场景块数量上限（硬约束）。实际可用数量还受『Token 预算』约束——token 先到上限即停止。" />
                            </div>
                            <span className="w-6 h-6 rounded-full bg-gradient-to-br from-emerald-500 to-teal-500 text-white text-xs flex items-center justify-center font-semibold">
                                {state.maxScenes}
                            </span>
                        </div>
                        <input
                            type="range" min={1} max={20} value={state.maxScenes}
                            onChange={(e) => actions.setMaxScenes(Number(e.target.value))}
                            className="w-full accent-emerald-500"
                        />
                        <p className="text-[10px] text-gray-400">实际受 Token 预算约束</p>
                    </div>

                    {/* MaxContextTokens */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-1">
                                <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Token 预算</label>
                                <ParamHint text="上下文允许的最大 token 数（硬约束）：超过即停止添加场景，实际上下文不会超出此值。建议按所用模型的上下文窗口设置。" />
                            </div>
                            <span className="text-xs font-mono text-gray-500">{state.maxContextTokens}</span>
                        </div>
                        <input
                            type="range" min={1000} max={16000} step={500} value={state.maxContextTokens}
                            onChange={(e) => actions.setMaxContextTokens(Number(e.target.value))}
                            className="w-full accent-amber-500"
                        />
                    </div>

                    {/* MaxAnswerTokens */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <div className="flex items-center gap-1">
                                <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">回答长度</label>
                                <ParamHint text="LLM 回答的最大 token 数；0 表示不限。限制回答长度可降低单次调用耗时与费用。" />
                            </div>
                            <span className="text-xs font-mono text-gray-500">
                                {state.maxAnswerTokens > 0 ? state.maxAnswerTokens + '字' : '不限'}
                            </span>
                        </div>
                        <input
                            type="range" min={0} max={2000} step={100} value={state.maxAnswerTokens}
                            onChange={(e) => actions.setMaxAnswerTokens(Number(e.target.value))}
                            className="w-full accent-rose-500"
                        />
                    </div>
                </div>
                {/* Status bar */}
                <div className="px-4 py-2 bg-teal-50 border-t border-teal-100 flex items-center gap-1.5">
                    <span className="w-2 h-2 rounded-full bg-teal-500 animate-pulse" />
                    <span className="text-xs text-teal-700">ChromaDB 已连接</span>
                </div>
            </div>
        </div>
    );
}
