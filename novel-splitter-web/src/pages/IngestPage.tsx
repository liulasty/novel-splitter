import { useState } from "react";
import { Link } from "react-router-dom";
import { FileInput, List } from "lucide-react";
import { cn } from "@/lib/utils";
import { useIngestTask } from "./Ingest/hooks/useIngestTask";
import { UploadPanel } from "./Ingest/components/UploadPanel";
import { NovelListTab } from "./Ingest/components/NovelListTab";

type TabKey = 'upload' | 'novels';

export default function IngestPage() {
    const [activeTab, setActiveTab] = useState<TabKey>('upload');
    const [highlightNovelId, setHighlightNovelId] = useState<string | undefined>(undefined);
    const { state, actions } = useIngestTask({
        onUploadSuccess: (novelId) => {
            setHighlightNovelId(novelId);
            setActiveTab('novels');
        },
    });

    const tabs: { key: TabKey; label: string; icon: typeof FileInput }[] = [
        { key: 'upload', label: '上传', icon: FileInput },
        { key: 'novels', label: '我的小说', icon: List },
    ];

    return (
        <div className="flex flex-col gap-5 max-w-4xl mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-orange-500 via-amber-500 to-violet-600 bg-clip-text text-transparent">
                    上传入库
                </h1>
                <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">
                    上传小说文件到知识库。上传完成后，请前往「<Link to="/process" className="text-indigo-600 font-medium hover:underline">场景处理</Link>」
                    页面进行章节解析、场景切分与向量化入库。
                </p>
            </div>

            {/* Tab bar */}
            <div className="flex gap-1 p-1 rounded-full bg-gray-100 w-fit">
                {tabs.map((t) => {
                    const Icon = t.icon;
                    const isActive = activeTab === t.key;
                    return (
                        <button
                            key={t.key}
                            type="button"
                            onClick={() => setActiveTab(t.key)}
                            className={cn(
                                "inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-sm font-medium transition-all",
                                isActive
                                    ? "bg-white text-indigo-600 shadow-sm ring-1 ring-gray-200"
                                    : "text-gray-500 hover:text-gray-700"
                            )}
                        >
                            <Icon className="w-4 h-4" />
                            {t.label}
                        </button>
                    );
                })}
            </div>

            {activeTab === 'upload' ? (
                <UploadPanel state={state} actions={actions} />
            ) : (
                <NovelListTab highlightNovelId={highlightNovelId} />
            )}
        </div>
    );
}
