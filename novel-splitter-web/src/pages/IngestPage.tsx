import { Zap, Database, GitBranch } from "lucide-react";
import { useIngestTask } from "./Ingest/hooks/useIngestTask";
import { UploadPanel } from "./Ingest/components/UploadPanel";
import { TaskQueueBoard } from "./Ingest/components/TaskQueueBoard";

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
                    上传小说并配置切分参数，由 Worker 异步写入向量知识库
                </p>
            </div>

            {/* Upload & Config Panel */}
            <UploadPanel state={state} actions={actions} />

            {/* Task Queue Board */}
            <TaskQueueBoard 
                tasks={state.tasks} 
                selectedTaskId={state.selectedTaskId} 
                actions={actions} 
            />
        </div>
    );
}