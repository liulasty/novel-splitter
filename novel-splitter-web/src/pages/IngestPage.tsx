import { Link } from "react-router-dom";
import { useIngestTask } from "./Ingest/hooks/useIngestTask";
import { UploadPanel } from "./Ingest/components/UploadPanel";
import { BaselineParsePanel } from "./Ingest/components/BaselineParsePanel";

export default function IngestPage() {
    const { state, actions } = useIngestTask();

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

            {/* Upload Panel */}
            <UploadPanel state={state} actions={actions} />

            {/* Stage 1: baseline chapter parse */}
            <BaselineParsePanel novelId={state.currentNovelId} />
        </div>
    );
}
