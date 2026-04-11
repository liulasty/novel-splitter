import { Zap, Database, GitBranch, Activity } from "lucide-react";
import { Link } from "react-router-dom";
import { useIngestTask } from "./Ingest/hooks/useIngestTask";
import { UploadPanel } from "./Ingest/components/UploadPanel";

export default function IngestPage() {
    const { state, actions } = useIngestTask();

    return (
        <div className="flex flex-col gap-5 max-w-4xl mx-auto">
            {/* Header */}
            <div>
                <h1 className="text-3xl font-bold bg-gradient-to-r from-orange-500 via-amber-500 to-violet-600 bg-clip-text text-transparent">
                    入库处理
                </h1>
                <p className="text-sm text-gray-500 mt-1.5 leading-relaxed">
                    <span className="inline-flex items-center gap-1 bg-amber-100 text-amber-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
                        <Zap className="w-3 h-3" /> RabbitMQ
                    </span>
                    <span className="inline-flex items-center gap-1 bg-violet-100 text-violet-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
                        <GitBranch className="w-3 h-3" /> 异步队列
                    </span>
                    <span className="inline-flex items-center gap-1 bg-teal-100 text-teal-700 text-xs font-medium px-2 py-0.5 rounded-full mr-1">
                        <Database className="w-3 h-3" /> ChromaDB
                    </span>
                    上传后在库中登记小说。<strong className="text-gray-700">章节解析</strong>与<strong className="text-gray-700">场景切分</strong>已拆成两步（独立 API / 队列），向量化在场景落库后进行。「书库列表」与「需要上传小说」分工选书 / 拉原文。亦支持
                    <span className="text-gray-600 font-medium"> 地址栏 ?novelId= </span>
                    与上次会话恢复。
                </p>
            </div>

            {/* Upload & Config Panel */}
            <UploadPanel state={state} actions={actions} />

            <div className="flex justify-end">
                <Link
                    to={state.currentNovelId ? `/tasks?novelId=${encodeURIComponent(state.currentNovelId)}` : '/tasks'}
                    className="inline-flex items-center gap-2 h-9 px-4 rounded-full text-sm font-medium text-indigo-700 bg-indigo-50 border border-indigo-100 hover:bg-indigo-100 transition-colors"
                >
                    <Activity className="w-4 h-4" />
                    查看任务监控
                </Link>
            </div>
        </div>
    );
}