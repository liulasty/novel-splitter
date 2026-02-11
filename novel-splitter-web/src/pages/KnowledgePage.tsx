import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";

export default function KnowledgePage() {
  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2">
         <h1 className="text-3xl font-bold tracking-tight text-gray-900">知识库管理</h1>
         <p className="text-gray-500">查看已入库小说的版本详情。</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
         {/* Demo Card */}
         <Card>
            <CardHeader>
               <CardTitle>剑来.txt</CardTitle>
               <CardDescription>最后更新: 2026-02-11</CardDescription>
            </CardHeader>
            <CardContent>
               <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                     <span className="text-gray-500">可用版本:</span>
                     <span className="font-medium">v1, v2-test</span>
                  </div>
                  <div className="flex justify-between text-sm">
                     <span className="text-gray-500">状态:</span>
                     <span className="inline-flex items-center rounded-full bg-green-50 px-2 py-1 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">就绪</span>
                  </div>
               </div>
            </CardContent>
         </Card>
      </div>
    </div>
  );
}
