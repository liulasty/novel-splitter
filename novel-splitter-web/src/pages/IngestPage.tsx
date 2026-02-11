import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { UploadCloud, FileText, Settings2 } from "lucide-react";

export default function IngestPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div className="flex flex-col gap-2">
         <h1 className="text-3xl font-bold tracking-tight text-gray-900">入库处理</h1>
         <p className="text-gray-500">上传小说并配置切分参数，将其转化为向量知识库。</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Upload Card */}
        <Card className="md:col-span-2 border-dashed border-2 border-gray-300 bg-gray-50/50 hover:bg-gray-50/80 transition-colors">
           <CardContent className="flex flex-col items-center justify-center py-12 text-center">
              <div className="p-4 rounded-full bg-blue-50 mb-4">
                 <UploadCloud className="w-8 h-8 text-blue-600" />
              </div>
              <h3 className="text-lg font-semibold mb-1">点击上传或拖拽文件</h3>
              <p className="text-sm text-gray-500 mb-4">支持 .txt 格式文件，最大 50MB</p>
              <button className="bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 px-4 py-2 rounded-md text-sm font-medium shadow-sm">
                 选择文件
              </button>
           </CardContent>
        </Card>

        {/* File List (Demo) */}
        <Card className="md:col-span-2">
           <CardHeader>
              <CardTitle className="flex items-center gap-2">
                 <FileText className="w-5 h-5 text-gray-500" />
                 待处理文件
              </CardTitle>
           </CardHeader>
           <CardContent>
              <div className="rounded-md border">
                 <div className="p-4 text-center text-sm text-gray-500 py-8">
                    暂无待处理文件，请先上传。
                 </div>
              </div>
           </CardContent>
        </Card>
      </div>
    </div>
  );
}
