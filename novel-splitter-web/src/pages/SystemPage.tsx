import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Database, Trash2 } from "lucide-react";

export default function SystemPage() {
  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div className="flex flex-col gap-2">
         <h1 className="text-3xl font-bold tracking-tight text-gray-900">系统管理</h1>
         <p className="text-gray-500">管理 ChromaDB 向量数据库状态。</p>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
         {/* Stats Card */}
         <Card>
            <CardHeader className="pb-2">
               <CardTitle className="text-sm font-medium text-gray-500">总文档数</CardTitle>
            </CardHeader>
            <CardContent>
               <div className="text-2xl font-bold">--</div>
               <p className="text-xs text-gray-500 mt-1">ChromaDB Store</p>
            </CardContent>
         </Card>
      </div>

      <Card className="border-red-100 bg-red-50/30">
         <CardHeader>
            <CardTitle className="text-red-700 flex items-center gap-2">
               <Trash2 className="w-5 h-5" />
               危险操作区
            </CardTitle>
            <CardDescription className="text-red-600/80">
               这些操作不可逆，请谨慎执行。
            </CardDescription>
         </CardHeader>
         <CardContent className="flex items-center justify-between">
            <div>
               <p className="font-medium text-gray-900">清空数据库</p>
               <p className="text-sm text-gray-500">永久删除所有向量数据。</p>
            </div>
            <button className="bg-red-600 text-white hover:bg-red-700 px-4 py-2 rounded-md text-sm font-medium shadow-sm transition-colors">
               确认清空
            </button>
         </CardContent>
      </Card>
    </div>
  );
}
