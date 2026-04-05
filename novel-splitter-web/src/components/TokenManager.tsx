import { useState, useEffect } from 'react';
import { Key, Save, Trash2, X, Settings } from 'lucide-react';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

export default function TokenManager() {
  const [isOpen, setIsOpen] = useState(false);
  const [token, setToken] = useState('');
  const [hasToken, setHasToken] = useState(false);

  // 初始化时从 localStorage 加载 Token
  useEffect(() => {
    const savedToken = localStorage.getItem('API_AUTH_TOKEN');
    if (savedToken) {
      setToken(savedToken);
      setHasToken(true);
    }
  }, []);

  // 保存 Token 到 localStorage
  const handleSave = () => {
    const trimmedToken = token.trim();
    if (!trimmedToken) {
      toast.error('请输入有效的 Token');
      return;
    }
    localStorage.setItem('API_AUTH_TOKEN', trimmedToken);
    setHasToken(true);
    toast.success('Token 已保存，刷新页面或继续操作即可生效');
    setIsOpen(false);
  };

  // 清除本地 Token
  const handleClear = () => {
    localStorage.removeItem('API_AUTH_TOKEN');
    setToken('');
    setHasToken(false);
    toast.info('Token 已从本地存储中清除');
  };

  return (
    <div className="fixed bottom-6 right-6 z-[100] flex flex-col items-end">
      {/* 弹出面板 */}
      {isOpen && (
        <div className="mb-4 w-80 overflow-hidden rounded-2xl bg-white/95 p-5 shadow-2xl backdrop-blur-sm ring-1 ring-black/5 animate-in fade-in zoom-in-95 slide-in-from-bottom-4 duration-200">
          <div className="mb-4 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-100 text-blue-600">
                <Key className="h-4 w-4" />
              </div>
              <div>
                <h3 className="text-sm font-bold text-gray-900">API 认证管理</h3>
                <p className="text-[10px] text-gray-500">用于 Task 3 接口权限测试</p>
              </div>
            </div>
            <button
              onClick={() => setIsOpen(false)}
              className="rounded-full p-1 text-gray-400 transition-colors hover:bg-gray-100 hover:text-gray-600"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          <div className="space-y-4">
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-gray-700">
                Auth Token (localStorage)
              </label>
              <div className="relative">
                <input
                  type="password"
                  value={token}
                  onChange={(e) => setToken(e.target.value)}
                  placeholder="在此粘贴您的 Token..."
                  className="w-full rounded-xl border border-gray-200 bg-gray-50/50 px-4 py-2.5 text-sm outline-none transition-all focus:border-blue-500 focus:ring-4 focus:ring-blue-500/10"
                />
              </div>
            </div>

            <div className="flex gap-2.5">
              <button
                onClick={handleSave}
                className="flex flex-1 items-center justify-center gap-2 rounded-xl bg-blue-600 py-2.5 text-sm font-semibold text-white shadow-md shadow-blue-200 transition-all hover:bg-blue-700 hover:shadow-lg active:scale-[0.98]"
              >
                <Save className="h-4 w-4" />
                保存配置
              </button>
              <button
                onClick={handleClear}
                className="flex items-center justify-center rounded-xl bg-red-50 px-3.5 py-2.5 text-sm font-semibold text-red-600 transition-all hover:bg-red-100 active:scale-[0.98]"
                title="清除 Token"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
            
            <div className="flex items-center justify-center gap-2 pt-1">
              <div className={cn(
                "h-1.5 w-1.5 rounded-full",
                hasToken ? "bg-green-500 animate-pulse" : "bg-gray-300"
              )} />
              <span className="text-[11px] font-medium text-gray-500">
                {hasToken ? "Token 已就绪" : "未检测到本地 Token"}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* 悬浮按钮 */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={cn(
          "group relative flex h-14 w-14 items-center justify-center rounded-full shadow-xl transition-all duration-300 hover:scale-110 active:scale-95",
          hasToken 
            ? "bg-gradient-to-br from-blue-500 to-indigo-600 text-white shadow-blue-200" 
            : "bg-white text-gray-600 ring-1 ring-gray-200 hover:bg-gray-50"
        )}
      >
        <Settings className={cn("h-6 w-6 transition-transform duration-500", isOpen ? "rotate-90" : "group-hover:rotate-45")} />
        
        {hasToken && !isOpen && (
          <span className="absolute -right-1 -top-1 flex h-4 w-4">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-green-400 opacity-75"></span>
            <span className="relative inline-flex h-4 w-4 rounded-full border-2 border-white bg-green-500"></span>
          </span>
        )}
      </button>
    </div>
  );
}