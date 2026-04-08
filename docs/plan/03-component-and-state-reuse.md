# 组件重构与状态复用执行细则

## 1. 背景与目标
手机端建设必须“复用业务逻辑、重写视图层”。本文件用于冻结复用边界，避免重复造轮子或视图强压导致的技术债。

## 2. 范围
### In Scope
- 复用 API 层、状态 hooks、类型定义。
- 建立移动端页面与组件目录结构。
- 定义组件职责边界与命名约定。

### Out Scope
- 重写后端接口。
- 在首期引入新的状态管理框架（维持 React Query + 现有 hooks）。

## 3. 复用资产清单
### 3.1 可直接复用
- API 客户端：`novel-splitter-web/src/api/client.ts`
- API 模块：`novel-splitter-web/src/api/*`
- 任务轮询逻辑：`novel-splitter-web/src/pages/Ingest/hooks/useTaskPoller.ts`
- 入库逻辑：`novel-splitter-web/src/pages/Ingest/hooks/useIngestTask.ts`
- 问答逻辑：`novel-splitter-web/src/pages/Chat/hooks/useChatLogic.ts`

### 3.2 必须重写（移动视图）
- 页面壳层：`MobileLayout`、`BottomTabBar`
- 页面展示组件：移动端卡片流、底部抽屉、步骤式上传组件
- 交互容器：顶部标题栏、沉浸式聊天容器

## 4. 建议目录结构
- `novel-splitter-web/src/pages/mobile/`
  - `HomePageMobile.tsx`
  - `IngestPageMobile.tsx`
  - `KnowledgePageMobile.tsx`
  - `ChatPageMobile.tsx`
- `novel-splitter-web/src/components/mobile/`
  - `MobileLayout.tsx`
  - `BottomTabBar.tsx`
  - `UploadStepper.tsx`
  - `TaskProgressCard.tsx`
  - `NovelCardList.tsx`
  - `BottomSheetSelector.tsx`

## 5. 组件职责边界
- **Container 组件**：负责调用 hooks、聚合数据、派发动作。
- **Presentational 组件**：只负责展示与交互事件抛出，不直接请求接口。
- **跨页面状态**：由 hooks + Query 缓存管理，避免新增全局单例状态污染。

## 6. 交互规范（移动优先）
- 点击热区最小高度 `44px`。
- 所有底部固定交互区域需适配安全区。
- Modal 优先替换为 Bottom Sheet 或全屏 Stepper。
- 列表页默认单列，支持骨架屏与空态。

## 7. 执行任务拆解
- FE-CMP-01：完成目录与命名规范落地。
- FE-CMP-02：抽离移动端容器组件（Layout、TabBar、TopBar）。
- FE-CMP-03：重写页面展示层并接入既有 hooks。
- FE-CMP-04：统一交互规范（点击区、安全区、空态）。

## 8. 验收标准（DoD）
- 移动端页面无直接硬编码 API 调用，均复用现有 API 层。
- PC 和 Mobile 可并行演进，彼此不互相阻塞。
- 相同业务状态在两端语义一致（任务状态、错误信息）。

## 9. 风险与待确认
- 现有 hooks 可能混有桌面交互假设，需在改造中清理 UI 耦合。
- 组件拆分后需补齐 Story/截图回归，避免视觉回退。
