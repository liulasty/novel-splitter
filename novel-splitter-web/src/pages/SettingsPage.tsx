import { useEffect, useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Loader2, Save, RefreshCw, Cpu, BrainCircuit, Database, Scissors } from "lucide-react";
import { settingsApi, SystemSettingsDto } from "@/api/settingsApi";
import { cn } from "@/lib/utils";
import { toast } from 'sonner';

type SettingsTab = 'embedding' | 'llm' | 'chroma' | 'splitStrategy';

export default function SettingsPage() {
    const queryClient = useQueryClient();
    const [settings, setSettings] = useState<SystemSettingsDto | null>(null);
    const [activeTab, setActiveTab] = useState<SettingsTab>('embedding');

    const { data: serverSettings, isLoading } = useQuery({
        queryKey: ['systemSettings'],
        queryFn: settingsApi.getSettings,
    });

    useEffect(() => {
        if (serverSettings) {
            setSettings(serverSettings);
        }
    }, [serverSettings]);

    const updateMutation = useMutation({
        mutationFn: settingsApi.updateSettings,
        onSuccess: () => {
            toast.success("配置已更新", { description: "后端服务已热加载最新配置" });
            queryClient.invalidateQueries({ queryKey: ['systemSettings'] });
        },
        onError: (error: any) => {
            toast.error("配置更新失败", { description: error.message || String(error) });
        }
    });

    const handleSave = () => {
        if (settings) {
            updateMutation.mutate(settings);
        }
    };

    const handleChange = (category: keyof SystemSettingsDto, field: string, value: any) => {
        setSettings(prev => {
            if (!prev) return prev;
            return {
                ...prev,
                [category]: {
                    ...prev[category],
                    [field]: value
                }
            };
        });
    };

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <Loader2 className="w-8 h-8 animate-spin text-blue-500" />
            </div>
        );
    }

    return (
        <div className="max-w-4xl mx-auto space-y-6">
            <div className="flex items-center justify-between">
                <div>
                    <h1 className="text-3xl font-bold tracking-tight text-gray-900">系统配置</h1>
                    <p className="text-gray-500 mt-1">管理底层服务参数，保存后支持热加载</p>
                </div>
                <div className="flex items-center gap-3">
                    <button
                        onClick={() => queryClient.invalidateQueries({ queryKey: ['systemSettings'] })}
                        className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
                    >
                        <RefreshCw className="w-4 h-4" /> 刷新
                    </button>
                    <button
                        onClick={handleSave}
                        disabled={updateMutation.isPending}
                        className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50"
                    >
                        {updateMutation.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                        保存配置
                    </button>
                </div>
            </div>

            {settings && (
                <div className="flex flex-col md:flex-row gap-6">
                    {/* Left Navigation Tabs */}
                    <div className="md:w-64 flex flex-col gap-1">
                        <button
                            onClick={() => setActiveTab('embedding')}
                            className={cn("w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors text-left", 
                                activeTab === 'embedding' ? "bg-blue-50 text-blue-700" : "text-gray-600 hover:bg-gray-100")}
                        >
                            <Cpu className="w-5 h-5" /> Embedding 模型
                        </button>
                        <button
                            onClick={() => setActiveTab('llm')}
                            className={cn("w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors text-left", 
                                activeTab === 'llm' ? "bg-blue-50 text-blue-700" : "text-gray-600 hover:bg-gray-100")}
                        >
                            <BrainCircuit className="w-5 h-5" /> LLM 后端
                        </button>
                        <button
                            onClick={() => setActiveTab('chroma')}
                            className={cn("w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors text-left", 
                                activeTab === 'chroma' ? "bg-blue-50 text-blue-700" : "text-gray-600 hover:bg-gray-100")}
                        >
                            <Database className="w-5 h-5" /> ChromaDB 配置
                        </button>
                        <button
                            onClick={() => setActiveTab('splitStrategy')}
                            className={cn("w-full flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors text-left", 
                                activeTab === 'splitStrategy' ? "bg-blue-50 text-blue-700" : "text-gray-600 hover:bg-gray-100")}
                        >
                            <Scissors className="w-5 h-5" /> 切分策略参数
                        </button>
                    </div>

                    {/* Right Content Area */}
                    <div className="flex-1">
                        {activeTab === 'embedding' && (
                            <Card>
                                <CardHeader>
                                    <CardTitle>Embedding 模型配置</CardTitle>
                                    <CardDescription>配置向量化模型名称及参数，支持本地部署或 API</CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-4">
                                    {Object.entries(settings.embedding || {}).map(([key, val]) => (
                                        <div key={key}>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">{key}</label>
                                            <input
                                                type="text"
                                                value={val as string}
                                                onChange={(e) => handleChange('embedding', key, e.target.value)}
                                                className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500"
                                            />
                                        </div>
                                    ))}
                                </CardContent>
                            </Card>
                        )}

                        {activeTab === 'llm' && (
                            <Card>
                                <CardHeader>
                                    <CardTitle>LLM 服务配置</CardTitle>
                                    <CardDescription>配置大语言模型的 API Key、Base URL 及核心参数</CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-4">
                                    {Object.entries(settings.llm || {}).map(([key, val]) => (
                                        <div key={key}>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">{key}</label>
                                            <input
                                                type={key.toLowerCase().includes('key') ? 'password' : 'text'}
                                                value={val as string}
                                                onChange={(e) => handleChange('llm', key, e.target.value)}
                                                className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500"
                                            />
                                        </div>
                                    ))}
                                </CardContent>
                            </Card>
                        )}

                        {activeTab === 'chroma' && (
                            <Card>
                                <CardHeader>
                                    <CardTitle>Chroma 数据库配置</CardTitle>
                                    <CardDescription>连接参数、主机地址及端口设置</CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-4">
                                    {Object.entries(settings.chroma || {}).map(([key, val]) => (
                                        <div key={key}>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">{key}</label>
                                            <input
                                                type="text"
                                                value={val as string}
                                                onChange={(e) => handleChange('chroma', key, e.target.value)}
                                                className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500"
                                            />
                                        </div>
                                    ))}
                                </CardContent>
                            </Card>
                        )}

                        {activeTab === 'splitStrategy' && (
                            <Card>
                                <CardHeader>
                                    <CardTitle>切分策略参数</CardTitle>
                                    <CardDescription>控制文档块的默认切分大小与重叠量</CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-4">
                                    {Object.entries(settings.splitStrategy || {}).map(([key, val]) => (
                                        <div key={key}>
                                            <label className="block text-sm font-medium text-gray-700 mb-1">{key}</label>
                                            <input
                                                type="number"
                                                value={val as number}
                                                onChange={(e) => handleChange('splitStrategy', key, Number(e.target.value))}
                                                className="w-full px-3 py-2 border border-gray-200 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500"
                                            />
                                        </div>
                                    ))}
                                </CardContent>
                            </Card>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}
