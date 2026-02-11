# 架构设计文档 (前端)

## 1. 引言
本文档描述 Novel Splitter 新版前端工程的架构设计。
采用 React 19 + Vite 6 + TypeScript 为核心技术栈。

## 2. 技术选型

| 类别 | 选型 | 说明 |
| :--- | :--- | :--- |
| **核心框架** | React 19 | 最新稳定版，利用 Actions 等新特性。 |
| **构建工具** | Vite 6 | 极速构建。 |
| **语言** | TypeScript 5.8+ | 强类型约束。 |
| **状态管理** | Zustand 5 | 轻量级全局状态管理。 |
| **数据请求** | TanStack Query 5 | 服务端状态管理、缓存、自动重试。 |
| **HTTP客户端** | Axios | 统一请求拦截、错误处理。 |
| **样式方案** | Tailwind CSS 4 | 下一代原子化 CSS 引擎。 |
| **UI组件库** | shadcn/ui | 高度可定制的组件集合 (基于 Radix UI)。 |
| **路由** | React Router 7 | 声明式路由管理。 |

## 3. 工程结构
推荐采用 Feature-based (按功能分层) 的目录结构，提高可维护性。

```
novel-splitter-web/
├── public/
├── src/
│   ├── api/                 # API 定义与 Axios 实例
│   │   ├── client.ts        # Axios 配置 (BaseURL, Interceptors)
│   │   ├── chatApi.ts
│   │   ├── novelApi.ts
│   │   └── systemApi.ts
│   ├── assets/
│   ├── components/          # 公共组件 (Atomic)
│   │   ├── ui/              # shadcn/ui 组件 (Button, Input, etc.)
│   │   └── Layout.tsx       # 全局布局 (Header, Sidebar)
│   ├── features/            # 业务功能模块
│   │   ├── chat/            # 对话模块
│   │   │   ├── components/  # 模块私有组件 (ChatBox, MessageList)
│   │   │   └── hooks/       # 模块私有 Hooks (useChatStream)
│   │   ├── ingest/          # 入库模块
│   │   ├── knowledge/       # 知识库模块
│   │   └── system/          # 系统管理模块
│   ├── hooks/               # 全局 Hooks (useToast, etc.)
│   ├── lib/                 # 工具库
│   │   ├── utils.ts         # cn() 等工具函数
│   │   └── format.ts        # date-fns 格式化封装
│   ├── pages/               # 页面路由组件
│   │   ├── ChatPage.tsx
│   │   ├── IngestPage.tsx
│   │   └── ...
│   ├── router/              # 路由配置
│   ├── stores/              # Zustand 全局状态
│   │   ├── useAppStore.ts
│   │   └── useChatStore.ts
│   ├── types/               # TypeScript 类型定义
│   ├── App.tsx
│   └── main.tsx
├── .env                     # 环境变量 (VITE_API_BASE_URL)
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

## 4. 关键设计决策

### 4.1 状态管理策略
- **服务端状态 (Server State)**: 使用 **TanStack Query** 管理。例如：小说列表、系统状态、对话历史。利用其缓存和重新验证机制减少手动请求。
- **客户端状态 (Client State)**: 使用 **Zustand** 管理。例如：当前选中的 Tab、用户输入的临时状态、UI 开关。

### 4.2 样式与主题
- 使用 Tailwind CSS 4 进行样式开发。
- 引入 `clsx` 和 `tailwind-merge` 解决样式冲突 (shadcn/ui 标配)。
- 响应式设计：保留 Grid 布局适配大屏。

### 4.3 路由设计
- `/chat`: 对话页 (默认首页)。
- `/knowledge`: 知识库管理页。
- `/ingest`: 入库页。
- `/system`: 系统管理页。

### 4.4 错误处理
- Axios 拦截器统一处理 500/404 错误。
- TanStack Query 配置全局 `onError` 回调，通过 Toast 提示用户网络错误。

## 5. 部署架构
- 开发环境：Vite Dev Server (Port 5173) 代理请求到后端 (Port 8080)。
- 生产环境：构建静态资源 (dist)，部署到 Spring Boot `static` 目录或独立 Nginx。
