import { useQuery } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Book, GitBranch, AlertCircle } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { knowledgeApi } from "@/api/knowledgeApi";

// Component to display versions for a single novel
function NovelVersionsCard({ novel }: { novel: string }) {
  const { data: versions, isLoading, isError } = useQuery({
    queryKey: ['versions', novel],
    queryFn: () => knowledgeApi.getVersions(novel),
  });

  return (
    <Card className="hover:shadow-md transition-shadow">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <Book className="w-5 h-5 text-blue-500" />
          {novel}
        </CardTitle>
        <CardDescription>
            {isLoading ? "加载版本信息..." : `共 ${versions?.length || 0} 个版本`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {isLoading ? (
          <div className="flex justify-center py-4">
            <Loader2 className="w-5 h-5 animate-spin text-gray-400" />
          </div>
        ) : isError ? (
            <div className="flex items-center gap-2 text-sm text-red-500 bg-red-50 p-2 rounded">
                <AlertCircle className="w-4 h-4" />
                获取版本失败
            </div>
        ) : (
          <div className="space-y-2">
            <div className="flex flex-wrap gap-2">
                {versions && versions.length > 0 ? (
                    versions.map((v) => (
                        <span key={v} className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700 border border-blue-100">
                            <GitBranch className="w-3 h-3 mr-1" />
                            {v}
                        </span>
                    ))
                ) : (
                    <span className="text-sm text-gray-400 italic">暂无版本数据</span>
                )}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

export default function KnowledgePage() {
  const { data: novels, isLoading: isNovelsLoading, isError: isNovelsError } = useQuery({
    queryKey: ['novels'],
    queryFn: novelApi.getNovels,
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
         <h1 className="text-3xl font-bold tracking-tight text-gray-900">知识库管理</h1>
         <p className="text-gray-500">查看已入库小说及其版本详情。</p>
      </div>

      {isNovelsLoading ? (
         <div className="flex flex-col items-center justify-center py-20">
            <Loader2 className="w-10 h-10 animate-spin text-blue-500 mb-4" />
            <p className="text-gray-500">正在加载小说列表...</p>
         </div>
      ) : isNovelsError ? (
         <Card className="border-red-200 bg-red-50">
             <CardContent className="flex items-center gap-3 p-6 text-red-700">
                 <AlertCircle className="w-6 h-6" />
                 <div>
                     <h3 className="font-semibold">加载失败</h3>
                     <p className="text-sm">无法获取小说列表，请检查后端服务是否运行。</p>
                 </div>
             </CardContent>
         </Card>
      ) : (
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
             {Array.isArray(novels) && novels.map((novel) => (
                 <NovelVersionsCard key={novel} novel={novel} />
             ))}
             
             {Array.isArray(novels) && novels.length === 0 && (
                 <div className="col-span-full text-center py-12 text-gray-500">
                     暂无已入库的小说，请前往“入库处理”页面上传。
                 </div>
             )}
          </div>
      )}
    </div>
  );
}
