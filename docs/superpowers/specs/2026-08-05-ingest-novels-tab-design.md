# 上传入库页 tab 化 + 小说卡片列表

- 日期：2026-08-05
- 状态：已确认，待实现

## 背景与动机

当前 `/ingest` 页在上传成功后把 `novelId` 写入 URL query param（`/ingest?novelId=xxx`），
页面据此定位小说并展示章节解析结果。用户希望：

1. 已上传成功的小说通过页面内的「列表 tab」以**卡片**形式查看，不再依赖 URL 路由导航。
2. 上传成功后自动切到列表并定位到新上传的小说卡片。

## 需求决策（已与用户确认）

1. **列表 tab 位置**：在 `/ingest` 页内部加两个 tab —— `上传` | `我的小说`。
2. **上传成功后的行为**：自动切到「我的小说」tab，新卡片置顶/高亮选中。
3. **章节展示位置**：在「我的小说」tab 内点卡片选中，卡片下方展示章节列表（与卡片同屏）。
4. **URL 移除范围**：仅移除 `/ingest` 页的 `?novelId=` URL 导航；`/process` 页的 `?novelId=` 深链保留。
5. **刷新行为**：刷新页面总是回到「上传」tab，不恢复列表选中状态。
6. **旧链接兼容**：完全忽略 `/ingest?novelId=xxx` 旧链接，既不读也不写 URL。
7. **实现方式**：新建自包含的 `NovelListTab` 组件。

## 设计

### 组件结构

```
IngestPage
├── TabBar（上传 | 我的小说，activeTab: 'upload' | 'novels'）
├── activeTab === 'upload'
│   └── UploadPanel（复用现有，仅上传 + 解析中状态）
└── activeTab === 'novels'
    └── NovelListTab（新建）
        ├── 小说卡片网格
        └── 选中卡片 → 章节详情（复用 BaselineParsePanel）
```

### 改动文件

| 文件 | 改动 |
|---|---|
| `src/pages/IngestPage.tsx` | 持 `activeTab` 状态；顶部 tab 切换 UI；上传成功回调 → 设 `highlightNovelId` + 切到 `novels` tab；按 tab 渲染 `UploadPanel` 或 `NovelListTab` |
| `src/pages/Ingest/hooks/useIngestTask.ts` | 删 `setSearchParams`（URL 写入）；删 URL / sessionStorage 初始化逻辑；`currentNovelId` 仅内存 state；暴露上传成功回调（供 IngestPage 切 tab） |
| `src/pages/Ingest/components/NovelListTab.tsx`（新建） | 见下 |
| `src/pages/Ingest/components/BaselineParsePanel.tsx` | 复用作选中卡片的章节详情；保留解析中（`isPolling`）与加载失败（`isError`）分支 |

### NovelListTab 组件

**Props**：
- `highlightNovelId?: string` —— 上传成功后要定位/选中的新小说 id。

**数据来源**：
- `novelApi.getNovelSummaries('all')` —— 小说列表
- `novelApi.getNovelStats()` —— 场景数统计（sceneCount/vectorCount）
- `taskApi.getAllTasks()` —— 判断 running（PENDING/PROCESSING）状态，卡片显示"解析中"

**卡片内容**：标题、状态徽标（PARSED/COMPLETED/解析中/FAILED）、更新时间、场景数。

**交互**：
- 卡片网格按更新时间倒序；新上传卡片置顶。
- 点击卡片 → `selectedNovelId` 高亮 → 下方渲染 `BaselineParsePanel`（复用）展示章节。
- 章节详情顶部提供「前往场景处理」链接（`/process?novelId=xxx`，深链保留）。
- 空态：「暂无已上传的小说，请先上传」。

### 数据流

1. 上传成功 → `useIngestTask` 回调 → `IngestPage` 设 `highlightNovelId` + `activeTab='novels'`。
2. `NovelListTab` 挂载：从 API 加载列表，用 `highlightNovelId` 定位并选中新卡片。
3. 选中卡片 → `getChapters(novelId)` 展示章节（解析中显示进度态，完成后自动刷新）。

### 错误处理

- 列表加载失败：显示错误提示，可重试。
- 章节加载失败：复用现有 `BaselineParsePanel` 的失败分支，显示"章节列表加载失败，请确认章节解析已完成"。
- 空列表：显示空态引导。

## 测试

前端项目无测试框架（仅 `tsc -b && vite build` + `eslint`）。
验证方式：

1. `npm run build` 通过（类型检查）。
2. 浏览器手动验证：
   - 上传小说 → 自动切「我的小说」tab → 新卡片高亮选中 → 章节展示
   - 解析中状态显示
   - 刷新后回到「上传」tab
   - 点击卡片展示章节、跳 `/process` 深链
