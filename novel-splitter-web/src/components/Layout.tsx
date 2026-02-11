import { Outlet, Link, useLocation } from 'react-router-dom';
import { MessageSquare, Database, FileInput, Settings } from 'lucide-react';
import { cn } from '@/lib/utils';

export default function Layout() {
  const location = useLocation();

  const navItems = [
    { path: '/', label: '对话问答', icon: MessageSquare },
    { path: '/knowledge', label: '知识库', icon: Database },
    { path: '/ingest', label: '入库处理', icon: FileInput },
    { path: '/system', label: '系统管理', icon: Settings },
  ];

  return (
    <div className="min-h-screen bg-gray-50/50 flex flex-col font-sans">
      {/* Glassmorphism Header */}
      <header className="sticky top-0 z-50 w-full glass border-b-0">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex h-16 items-center justify-between">
            {/* Logo Area */}
            <div className="flex items-center gap-2">
              <div className="h-8 w-8 rounded-lg bg-gradient-to-br from-blue-500 to-indigo-600 shadow-lg flex items-center justify-center">
                <span className="text-white font-bold text-lg">N</span>
              </div>
              <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-gray-900 to-gray-600">
                Novel Splitter
              </span>
            </div>

            {/* Navigation */}
            <nav className="hidden md:flex items-center gap-1">
              {navItems.map((item) => {
                const Icon = item.icon;
                const isActive = location.pathname === item.path;
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

            {/* Mobile Menu Button (Placeholder for now) */}
            <div className="md:hidden">
              <button className="p-2 rounded-md text-gray-500 hover:bg-gray-100">
                <span className="sr-only">Open menu</span>
                <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                </svg>
              </button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content with fade-in animation */}
      <main className="flex-1 w-full max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 animate-in fade-in duration-500">
        <Outlet />
      </main>
    </div>
  );
}
