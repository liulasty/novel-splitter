import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";

export default function ChatPage() {
  return (
    <div className="grid gap-6 md:grid-cols-[300px_1fr] h-[calc(100vh-8rem)]">
      {/* Sidebar / Config Area */}
      <div className="flex flex-col gap-4">
        <Card>
          <CardHeader>
            <CardTitle>会话设置</CardTitle>
            <CardDescription>选择小说版本和检索参数</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
             {/* Placeholders for Select inputs */}
             <div className="space-y-2">
                <label className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70">选择小说</label>
                <div className="h-10 w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-500">
                   -- 暂无数据 --
                </div>
             </div>
             <div className="space-y-2">
                <label className="text-sm font-medium leading-none">引用数量 (TopK)</label>
                <div className="h-10 w-full rounded-md border border-gray-200 bg-white px-3 py-2 text-sm text-gray-500">
                   3
                </div>
             </div>
          </CardContent>
        </Card>
      </div>

      {/* Main Chat Area */}
      <Card className="flex flex-col h-full border-0 shadow-xl bg-white/80">
        <div className="flex-1 p-6 overflow-y-auto prose prose-slate max-w-none">
           <div className="flex flex-col gap-6">
              {/* Demo Messages */}
              <div className="flex justify-start">
                 <div className="bg-white border p-4 rounded-2xl rounded-tl-none shadow-sm max-w-[80%]">
                    <p className="m-0 text-sm text-gray-800">你好！我是 Novel Splitter 助手。请先在左侧选择一本小说，我将根据原文回答你的问题。</p>
                 </div>
              </div>
           </div>
        </div>
        <div className="p-4 border-t bg-white/50 backdrop-blur-sm rounded-b-xl">
           <div className="flex gap-2">
              <input 
                type="text" 
                placeholder="输入你的问题..." 
                className="flex h-10 w-full rounded-full border border-gray-200 bg-white px-4 py-2 text-sm ring-offset-white file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-gray-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              />
              <button className="inline-flex items-center justify-center whitespace-nowrap rounded-full text-sm font-medium ring-offset-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50 bg-blue-600 text-white hover:bg-blue-700 h-10 px-6 py-2">
                 发送
              </button>
           </div>
        </div>
      </Card>
    </div>
  );
}
