import { useEffect, useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { MessageSquare, Database, FileInput, Settings, Bug, Server, Menu, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import TokenManager from './TokenManager';

export default function Layout() {
  const location = useLocation();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const navItems = [
    { path: '/', label: '对话问答', icon: MessageSquare },
    { path: '/knowledge', label: '知识库', icon: Database },
    { path: '/ingest', label: '入库处理', icon: FileInput },
    { path: '/debug', label: 'RAG 调试', icon: Bug },
    { path: '/system', label: '系统管理', icon: Settings },
    { path: '/chroma-admin', label: 'Chroma 管理', icon: Server },
  ];

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [location.pathname]);

  const isItemActive = (path: string) => {
    if (path === '/') {
      return location.pathname === '/';
    }

    return location.pathname.startsWith(path);
  };

  return (
    <div className="min-h-screen bg-gray-50/50 flex flex-col font-sans">
      <header className="sticky top-0 z-50 w-full glass border-b-0">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 shadow-lg flex items-center justify-center">
                <span className="text-white font-bold text-lg">N</span>
              </div>
              <span className="max-w-[10rem] truncate text-base font-bold bg-clip-text text-transparent bg-gradient-to-r from-gray-900 to-gray-600 sm:max-w-none sm:text-xl">
                Novel Splitter
              </span>
            </div>

            <nav className="hidden md:flex items-center gap-1">
              {navItems.map((item) => {
                const Icon = item.icon;
                const isActive = isItemActive(item.path);
                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={cn(
                      "flex items-center px-4 py-2 rounded-full text-sm font-medium transition-all duration-200",
                      isActive
                        ? "bg-blue-50 text-blue-600 shadow-sm ring-1 ring-blue-100"
                        : "text-gray-600 hover:bg-gray-100/80 hover:text-gray-900"
                    )}
                  >
                    <Icon className={cn("w-4 h-4 mr-2", isActive ? "text-blue-500" : "text-gray-500")} />
                    {item.label}
                  </Link>
                );
              })}
            </nav>

            <div className="md:hidden">
              <button
                type="button"
                className="inline-flex items-center justify-center rounded-full p-2 text-gray-600 transition-colors hover:bg-gray-100/80 hover:text-gray-900"
                aria-label={mobileMenuOpen ? '关闭导航菜单' : '打开导航菜单'}
                aria-expanded={mobileMenuOpen}
                onClick={() => setMobileMenuOpen((open) => !open)}
              >
                {mobileMenuOpen ? <X className="h-6 w-6" /> : <Menu className="h-6 w-6" />}
              </button>
            </div>
          </div>

          <div
            className={cn(
              'overflow-hidden transition-all duration-200 md:hidden',
              mobileMenuOpen ? 'max-h-96 pb-4' : 'max-h-0'
            )}
          >
            <nav className="space-y-2 border-t border-white/40 pt-3">
              {navItems.map((item) => {
                const Icon = item.icon;
                const isActive = isItemActive(item.path);

                return (
                  <Link
                    key={item.path}
                    to={item.path}
                    className={cn(
                      'flex items-center rounded-2xl px-4 py-3 text-sm font-medium transition-all duration-200',
                      isActive
                        ? 'bg-blue-50 text-blue-600 shadow-sm ring-1 ring-blue-100'
                        : 'text-gray-600 hover:bg-gray-100/80 hover:text-gray-900'
                    )}
                  >
                    <Icon className={cn('mr-3 h-4 w-4', isActive ? 'text-blue-500' : 'text-gray-500')} />
                    {item.label}
                  </Link>
                );
              })}
            </nav>
          </div>
        </div>
      </header>

      <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-in fade-in duration-500">
        <Outlet />
      </main>
      
      {/* Token 管理组件 */}
      <TokenManager />
    </div>
  );
}
