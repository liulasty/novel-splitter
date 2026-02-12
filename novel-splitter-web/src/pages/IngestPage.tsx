import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent } from "@/components/ui/card";
import { UploadCloud, FileText, Loader2, CheckCircle, AlertCircle } from "lucide-react";
import { novelApi } from "@/api/novelApi";
import { cn } from "@/lib/utils";

export default function IngestPage() {
  const queryClient = useQueryClient();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [uploadedFileName, setUploadedFileName] = useState<string>("");
  const [version, setVersion] = useState("v1");
  const [maxScenes, setMaxScenes] = useState(0);
  const [ingestStatus, setIngestStatus] = useState<string>("");

  // Upload Mutation
  const uploadMutation = useMutation({
    mutationFn: novelApi.uploadNovel,
    onSuccess: (data) => {
      setIngestStatus(`上传成功: ${data.message}`);
      if (data.fileName) {
        setUploadedFileName(data.fileName);
      }
      queryClient.invalidateQueries({ queryKey: ['novels'] });
    },
    onError: (error: any) => {
      const msg = error.response?.data?.error || error.message || "Unknown error";
      setIngestStatus(`上传失败: ${msg}`);
    }
  });

  // Ingest Mutation
  const ingestMutation = useMutation({
    mutationFn: novelApi.ingestNovel,
    onSuccess: (data) => {
      setIngestStatus(`入库成功: ${data.message}`);
    },
    onError: (error: any) => {
      const msg = error.response?.data?.error || error.message || "Unknown error";
      setIngestStatus(`入库失败: ${msg}`);
    }
  });

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
      setUploadedFileName(""); // Reset uploaded filename on new selection
      setIngestStatus("");
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    uploadMutation.mutate(selectedFile);
  };

  const handleIngest = async () => {
    if (!uploadedFileName) {
        if (selectedFile) {
            setIngestStatus("请先点击'上传文件'按钮完成上传");
        } else {
             setIngestStatus("请先选择并上传文件");
        }
        return;
    }
    
    const fileName = uploadedFileName;
    
    if (!fileName) {
        setIngestStatus("无法获取文件名");
        return;
    }

    ingestMutation.mutate({
      fileName: fileName,
      version: version,
      maxScenes: maxScenes
    });
  };

  return (
    <div className="max-w-4xl mx-auto space-y-8">
      <div className="flex flex-col gap-2">
         <h1 className="text-3xl font-bold tracking-tight text-gray-900">入库处理</h1>
         <p className="text-gray-500">上传小说并配置切分参数，将其转化为向量知识库。</p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Upload & Config Card */}
        <Card className="md:col-span-2 border-dashed border-2 border-gray-300 bg-gray-50/50 hover:bg-gray-50/80 transition-colors">
           <CardContent className="flex flex-col items-center justify-center py-12 text-center space-y-6">
              
              {/* File Input Area */}
              <div className="w-full max-w-md space-y-4">
                  <div className="flex flex-col items-center justify-center p-6 border-2 border-dashed border-blue-200 rounded-xl bg-blue-50/30">
                      <UploadCloud className="w-12 h-12 text-blue-500 mb-2" />
                      <label htmlFor="file-upload" className="cursor-pointer">
                          <span className="bg-white px-4 py-2 rounded-md shadow-sm border border-gray-300 text-sm font-medium text-gray-700 hover:bg-gray-50 transition-colors">
                              {selectedFile ? "更改文件" : "选择文件"}
                          </span>
                          <input id="file-upload" type="file" accept=".txt" className="hidden" onChange={handleFileChange} />
                      </label>
                      <p className="mt-2 text-sm text-gray-500">
                          {selectedFile ? selectedFile.name : "支持 .txt 格式文件，最大 50MB"}
                      </p>
                  </div>

                  {/* Config Inputs */}
                  <div className="grid grid-cols-2 gap-4 text-left">
                      <div className="space-y-1">
                          <label className="text-sm font-medium text-gray-700">版本号 (Version)</label>
                          <input 
                            type="text" 
                            value={version}
                            onChange={(e) => setVersion(e.target.value)}
                            className="w-full h-9 rounded-md border border-gray-300 bg-white px-3 py-1 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                            placeholder="v1"
                          />
                      </div>
                      <div className="space-y-1">
                          <label className="text-sm font-medium text-gray-700">最大场景数 (0=全部)</label>
                          <input 
                            type="number" 
                            value={maxScenes}
                            onChange={(e) => setMaxScenes(Number(e.target.value))}
                            className="w-full h-9 rounded-md border border-gray-300 bg-white px-3 py-1 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
                          />
                      </div>
                  </div>

                  {/* Actions */}
                  <div className="flex gap-3 justify-center pt-2">
                      <button 
                        onClick={handleUpload}
                        disabled={!selectedFile || uploadMutation.isPending}
                        className={cn(
                            "flex items-center gap-2 px-6 py-2 rounded-full text-sm font-medium transition-all shadow-sm",
                            uploadMutation.isPending ? "bg-gray-100 text-gray-400" : "bg-white border border-gray-300 text-gray-700 hover:bg-gray-50 hover:shadow-md"
                        )}
                      >
                        {uploadMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <UploadCloud className="w-4 h-4" />}
                        上传文件
                      </button>
                      
                      <button 
                        onClick={handleIngest}
                        disabled={!selectedFile || ingestMutation.isPending}
                        className={cn(
                            "flex items-center gap-2 px-6 py-2 rounded-full text-sm font-medium transition-all shadow-sm text-white",
                            ingestMutation.isPending 
                                ? "bg-blue-300 cursor-not-allowed" 
                                : "bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-700 hover:to-indigo-700 hover:shadow-lg hover:scale-[1.02]"
                        )}
                      >
                        {ingestMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                        开始入库
                      </button>
                  </div>
              </div>

              {/* Status Message */}
              {(ingestStatus || uploadMutation.isError || ingestMutation.isError) && (
                  <div className={cn(
                      "flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium animate-in fade-in slide-in-from-bottom-2",
                      (uploadMutation.isError || ingestMutation.isError) ? "bg-red-50 text-red-600" : "bg-green-50 text-green-600"
                  )}>
                      {(uploadMutation.isError || ingestMutation.isError) ? <AlertCircle className="w-4 h-4" /> : <CheckCircle className="w-4 h-4" />}
                      {ingestStatus}
                  </div>
              )}

           </CardContent>
        </Card>
      </div>
    </div>
  );
}
