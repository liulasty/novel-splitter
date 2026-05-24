interface ChatSidebarProps {
    state: {
        novels: Array<{ novelId: string; title: string }> | undefined;
        profileOptions: { index: number; label: string }[];
        selectedNovel: string; // novelId
        selectedProfileIndex: number;
        topK: number;
        maxScenes: number;
        maxContextTokens: number;
        maxAnswerTokens: number;
    };
    actions: {
        setSelectedNovel: (val: string) => void;
        setSelectedProfileIndex: (val: number) => void;
        setTopK: (val: number) => void;
        setMaxScenes: (val: number) => void;
        setMaxContextTokens: (val: number) => void;
        setMaxAnswerTokens: (val: number) => void;
    };
}

export function ChatSidebar({ state, actions }: ChatSidebarProps) {
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
                        <select
                            className="w-full h-9 rounded-lg border border-gray-200 bg-gray-50 px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-violet-400"
                            value={state.selectedNovel}
                            onChange={(e) => actions.setSelectedNovel(e.target.value)}
                        >
                            <option value="" disabled>-- 请选择 --</option>
                            {Array.isArray(state.novels) && state.novels.map(n => (
                                <option key={n.novelId} value={n.novelId}>{n.title}</option>
                            ))}
                        </select>
                    </div>

                    {/* Split profile (version + chunk / overlap) */}
                    <div className="space-y-1.5">
                        <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">数据集（版本 / 滑窗）</label>
                        <select
                            className="w-full h-9 rounded-lg border border-gray-200 bg-gray-50 px-3 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-teal-400 disabled:opacity-50"
                            value={state.profileOptions.length ? String(state.selectedProfileIndex) : ""}
                            onChange={(e) => actions.setSelectedProfileIndex(Number(e.target.value))}
                            disabled={!state.selectedNovel || !state.profileOptions?.length}
                        >
                            <option value="" disabled>-- 请选择 --</option>
                            {state.profileOptions.map((o) => (
                                <option key={o.index} value={String(o.index)}>{o.label}</option>
                            ))}
                        </select>
                    </div>

                    {/* TopK */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">引用数量 TopK</label>
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
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">上下文场景上限</label>
                            <span className="w-6 h-6 rounded-full bg-gradient-to-br from-emerald-500 to-teal-500 text-white text-xs flex items-center justify-center font-semibold">
                                {state.maxScenes}
                            </span>
                        </div>
                        <input
                            type="range" min={1} max={20} value={state.maxScenes}
                            onChange={(e) => actions.setMaxScenes(Number(e.target.value))}
                            className="w-full accent-emerald-500"
                        />
                    </div>

                    {/* MaxContextTokens */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Token 预算</label>
                            <span className="text-xs font-mono text-gray-500">{state.maxContextTokens}</span>
                        </div>
                        <input
                            type="range" min={1000} max={8000} step={500} value={state.maxContextTokens}
                            onChange={(e) => actions.setMaxContextTokens(Number(e.target.value))}
                            className="w-full accent-amber-500"
                        />
                    </div>

                    {/* MaxAnswerTokens */}
                    <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                            <label className="text-xs font-semibold text-gray-400 uppercase tracking-wide">回答长度</label>
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