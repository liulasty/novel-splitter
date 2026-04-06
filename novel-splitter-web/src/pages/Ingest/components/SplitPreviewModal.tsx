import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { splitApi, SplitPreviewRequestDto, ChunkPreviewDto } from '@/api/splitApi';
import { Loader2, Play, X, FileText, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';

interface SplitPreviewModalProps {
    isOpen: boolean;
    onClose: () => void;
}

export function SplitPreviewModal({ isOpen, onClose }: SplitPreviewModalProps) {
    const [sourceText, setSourceText] = useState('');
    const [strategy, setStrategy] = useState('scene');
    const [maxTokens, setMaxTokens] = useState(1200);
    const [overlapTokens, setOverlapTokens] = useState(0);
    const [chunks, setChunks] = useState<ChunkPreviewDto[]>([]);

    const previewMutation = useMutation({
        mutationFn: splitApi.previewSplit,
        onSuccess: (data) => {
            setChunks(data);
            toast.success('预览切分成功', { description: `共切分为 ${data.length} 个区块` });
        },
        onError: (error: any) => {
            toast.error('预览切分失败', { description: error.message || String(error) });
        }
    });

    const handlePreview = () => {
        if (!sourceText.trim()) {
            toast.error('请输入测试文本');
            return;
        }
        previewMutation.mutate({
            sourceText,
            strategy,
            maxTokens,
            overlapTokens
        });
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4 sm:p-6">
            <div className="bg-white rounded-2xl w-full max-w-6xl max-h-[90vh] shadow-2xl flex flex-col overflow-hidden">
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100 bg-gray-50/50">
                    <h2 className="text-xl font-semibold text-gray-900 flex items-center gap-2">
                        <FileText className="w-5 h-5 text-indigo-600" />
                        内存切分效果预览
                    </h2>
                    <button
                        onClick={onClose}
                        className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-full transition-colors"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* Body */}
                <div className="flex flex-1 overflow-hidden">
                    {/* Left: Input & Config */}
                    <div className="w-1/3 flex flex-col border-r border-gray-100 bg-white">
                        <div className="p-4 border-b border-gray-100 space-y-4 bg-gray-50/30">
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">切分策略</label>
                                <select
                                    value={strategy}
                                    onChange={(e) => setStrategy(e.target.value)}
                                    className="w-full text-sm border-gray-200 rounded-lg shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                                >
                                    <option value="scene">Scene (按场景)</option>
                                    <option value="semantic">Semantic (语义聚合)</option>
                                    <option value="overlap">Overlap (重叠切分)</option>
                                </select>
                            </div>
                            <div className="grid grid-cols-2 gap-3">
                                <div>
                                    <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">最大 Token</label>
                                    <input
                                        type="number"
                                        value={maxTokens}
                                        onChange={(e) => setMaxTokens(Number(e.target.value))}
                                        className="w-full text-sm border-gray-200 rounded-lg shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                                    />
                                </div>
                                <div>
                                    <label className="block text-xs font-semibold text-gray-500 uppercase tracking-wider mb-1.5">重叠 Token</label>
                                    <input
                                        type="number"
                                        value={overlapTokens}
                                        onChange={(e) => setOverlapTokens(Number(e.target.value))}
                                        className="w-full text-sm border-gray-200 rounded-lg shadow-sm focus:border-indigo-500 focus:ring-indigo-500"
                                    />
                                </div>
                            </div>
                            <button
                                onClick={handlePreview}
                                disabled={previewMutation.isPending}
                                className="w-full flex items-center justify-center gap-2 bg-indigo-600 text-white px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-indigo-700 transition-colors disabled:opacity-50"
                            >
                                {previewMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Play className="w-4 h-4" />}
                                执行实时切分
                            </button>
                        </div>
                        <div className="flex-1 p-4 flex flex-col gap-2">
                            <label className="text-xs font-semibold text-gray-500 uppercase tracking-wider">测试文本内容</label>
                            <textarea
                                value={sourceText}
                                onChange={(e) => setSourceText(e.target.value)}
                                placeholder="粘贴你要测试的小说片段..."
                                className="flex-1 w-full resize-none rounded-lg border-gray-200 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm p-3"
                            />
                        </div>
                    </div>

                    {/* Right: Results */}
                    <div className="w-2/3 bg-slate-50 flex flex-col relative">
                        {previewMutation.isPending ? (
                            <div className="absolute inset-0 flex flex-col items-center justify-center bg-white/50 backdrop-blur-sm z-10">
                                <Loader2 className="w-8 h-8 animate-spin text-indigo-500 mb-4" />
                                <p className="text-sm text-gray-600 font-medium">正在进行内存切分计算...</p>
                            </div>
                        ) : null}
                        
                        <div className="flex-1 overflow-y-auto p-6 space-y-4">
                            {chunks.length === 0 ? (
                                <div className="h-full flex flex-col items-center justify-center text-gray-400">
                                    <FileText className="w-12 h-12 mb-3 text-gray-300" />
                                    <p className="text-sm">左侧输入文本并点击执行预览</p>
                                </div>
                            ) : (
                                chunks.map((chunk, idx) => (
                                    <div key={idx} className="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
                                        <div className="flex items-center justify-between px-4 py-2 bg-gray-50/80 border-b border-gray-100">
                                            <span className="text-xs font-semibold text-gray-500">Chunk #{chunk.index + 1}</span>
                                            <div className="flex items-center gap-2">
                                                <span className={cn(
                                                    "px-2 py-0.5 rounded text-[10px] font-medium tracking-wide",
                                                    chunk.type === 'SCENE' ? "bg-blue-100 text-blue-700" :
                                                    chunk.type === 'DIALOGUE' ? "bg-amber-100 text-amber-700" :
                                                    "bg-gray-100 text-gray-700"
                                                )}>
                                                    {chunk.type}
                                                </span>
                                                <span className="text-xs text-gray-400">长度: {chunk.length}</span>
                                            </div>
                                        </div>
                                        <div className="p-4 text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">
                                            {chunk.text}
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
