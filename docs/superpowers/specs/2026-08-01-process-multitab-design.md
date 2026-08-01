# /process 页多 Tab 重设计

日期：2026-08-01
状态：已批准（设计评审通过）

## 问题背景

`/process` 页（场景处理）把三个管线阶段的操作混在一个面板里：小说选择器、识别策略、版本/chunk 参数、解析/切分/入库的全部按钮、任务状态堆叠在单个 `ProcessingPanel`（16KB+，CLAUDE.md 已知问题之一）里。用户要求用多 tab 隔离这些操作。

## 目标

- 用三个 tab 隔离三个管线阶段：**章节解析 / 场景切分 / 向量化入库**。
- 把 16KB+ 的 `ProcessingPanel` 拆为 Panel 骨架 + 三个聚焦子组件。
- 复用上一轮版本修复打下的 `useProcessTask`（含 `useSplitVersion`），不重写状态层。

## 决策（已确认）

1. **Tab 划分**：三阶段流水线（章节解析 / 场景切分 / 向量化入库）。
2. **小说选择器**：共享，固定在 tab 栏上方（所有阶段都需要）。
3. **版本/chunk 参数**：只放「场景切分」tab；「向量化入库」tab 显示只读摘要（如「将向量化 v2 (512/64)」）。
4. **tab 导航**：自由切换 + 按钮状态门控（沿用现有 `canSceneSplit` 等逻辑，tab 本身不锁门）。
5. **任务状态区**：全局固定，放在 tab 栏下方，任何 tab 都可见。

## 范围界定

**做**：
- `ProcessingPanel` 改为骨架（小说选择器 + tab 栏 + 活动 tab + 全局任务状态 + 外链）。
- 新建 `ParseTab` / `SplitTab` / `EmbedTab` 三个子组件。
- Modal 归属下放：`chapterReviewOpen` → ParseTab，`previewOpen` → SplitTab。
- `activeTab` 存 URL `?tab=parse|split|embed`（非法回退 `parse`）。

**不做**：
- 改 `useProcessTask` 状态层（它已集中全部共享状态，无需动）。
- 改后端；改其他页面。
- 引入前端测试框架（无 vitest/jest，沿用 tsc + 手工 E2E）。

## 组件结构

```
ProcessPage.tsx（路由组件，基本不变）
 └─ ProcessingPanel.tsx（改为 Panel 骨架）
     ├─ 小说选择器（共享，不动）
     ├─ Tab 栏（章节解析 / 场景切分 / 向量化入库）
     ├─ 活动 tab 内容：
     │    ├─ ParseTab.tsx（新）
     │    ├─ SplitTab.tsx（新）
     │    └─ EmbedTab.tsx（新）
     ├─ TaskPollerStatus（全局固定，不动）
     └─ 子组件各自渲染 Modal（ChapterReviewModal、SplitPreviewModal）
```

## 各 tab 内容

### ParseTab（章节解析）
| 元素 | 绑定 |
|---|---|
| 识别策略 select | `recognitionStrategy` → `setRecognitionStrategy` |
| 章节正则 input（仅 CUSTOM 策略时显示） | `chapterTitleRegex` → `setChapterTitleRegex` |
| ① 解析章节 | `handleChapterParse`（门控：`!currentNovelId \|\| isChapterParsing \|\| chapterParseBusy`） |
| 强制重解析 | `handleForceReparseChapters` |
| 章节校对 | 打开 `ChapterReviewModal`（`chapterReviewOpen` 状态在此持有） |

### SplitTab（场景切分）
| 元素 | 绑定 |
|---|---|
| 版本标识复合控件（下拉已有数据集 + 新建版本输入 + chunk 提示） | `version`/`setVersion`/`profiles`/`currentProfile` |
| 场景块大小 / 重叠 | `maxTokens` / `overlapTokens` |
| ② 场景切分 / ② 切分并入库 | `handleSceneSplit(false/true)`（门控：`!currentNovelId \|\| !canSceneSplit \|\| isSceneSplitting`） |
| 预览效果 | 打开 `SplitPreviewModal`（`previewOpen` 状态在此持有） |

### EmbedTab（向量化入库）
| 元素 | 绑定 |
|---|---|
| 只读摘要 | 「将向量化 `{version}`（`{chunkSize}/{chunkOverlap}`）」；`currentProfile` 缺失时提示"版本尚未切分完成，请先到「场景切分」生成数据集" |
| ③ 仅向量化 | `handleEmbed`（门控：`!currentNovelId \|\| isEmbedding`） |

## 状态流与 tab 切换

- 所有共享状态仍在 `useProcessTask`（小说、版本、chunk 参数、任务轮询）。三个子组件接收同一个 `state`/`actions`，各自解构所需字段。
- `activeTab` 用 `useSearchParams` 读写 `?tab=`：
  - 初始化：`tab` 参数 ∈ {parse, split, embed} 则用之，否则回退 `parse`。
  - 切换：`setSearchParams(prev => { p.set('tab', tab); return p; }, { replace: true })`。
- 切换 tab 只是条件渲染，无状态丢失。
- Modal 触发状态由各自 tab 持有，互不干扰。

## 边界情况

| 场景 | 行为 |
|---|---|
| 无小说 | 各 tab 按钮禁用 + 提示"请先选择小说"（选择器在 tab 栏上方，无需切 tab） |
| 无版本 / 无 profile | SplitTab 下拉显示"暂无已生成版本"；EmbedTab 显示切分引导 |
| 切 tab | 状态保留（`useProcessTask` 集中持有） |
| 深链 `?tab=embed` | 直接落在入库 tab，按钮按小说状态门控 |
| 非法 `?tab=xxx` | 回退 `parse` |

## 错误处理

- 各操作按钮已通过 mutation 的 toast 处理错误（解析失败、切分 409、入库失败等），无新增错误路径。
- 无新增校验。

## 测试

- 前端无测试框架，本期不引入；以 `npm run build`（tsc 类型校验）+ 浏览器手工 E2E 覆盖：
  1. 三 tab 正确渲染，各 tab 只显示对应阶段的参数与按钮
  2. 操作按钮门控正确（未解析时切分灰掉等）
  3. tab 切换不丢状态（版本选择、参数保持）
  4. 章节校对 / 切分预览两个 Modal 从对应 tab 正常打开
  5. `?tab=split` 深链直达；`?tab=xxx` 回退 `parse`
  6. 全局任务状态区在所有 tab 可见
