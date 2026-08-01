# /knowledge 页表格化重设计

日期：2026-08-01
状态：已批准（设计评审通过）

## 问题背景

用户指出 `/knowledge` 知识库管理页三个痛点：
1. **找不到书**——书多时按状态分区堆叠、无搜索/过滤/排序。
2. **操作繁琐**——删除/版本操作藏在 hover 后才出现、确认步骤多、purge 勾选重复。
3. **信息展示不清晰**——卡片信息少且杂，状态/版本/向量数不直观。

## 目标

- 改为**可搜索 / 可过滤 / 可排序的表格** + 可展开版本行。
- 操作更直接：整书删除用共享确认弹窗，版本删除在展开区。
- 信息层级清晰：状态徽标、版本数、场景/向量数、更新时间一列一列看。

## 决策（已确认）

1. **布局范式**：表格 + 可展开行（替代原状态分区卡片网格）。
2. **版本/chunk 参数**：在展开行中展示，逐版本删除。
3. **整书删除**：共享确认弹窗（含「同时删除任务记录」勾选），替代卡片内联确认。
4. **统计列**：用现有 `getNovelStats`（`NovelStatRecordDto`：sceneCount/vectorCount）合并展示；无数据显 `—`。

## 范围界定

**做**：
- 重组 `KnowledgePage.tsx`：工具栏（搜索/过滤/排序）+ 表格 + 展开行。
- 新建 `NovelTable.tsx`、`NovelTableRow.tsx`、`DeleteNovelModal.tsx`。
- 复用 `VersionTag.tsx`（版本芯片 + 内联删除确认）。
- 废弃 `NovelVersionsCard.tsx`（被表格取代）。

**不做**：
- 改后端；改其他页面。
- 引入前端测试框架（沿用 tsc + 手工 E2E）。
- 改动 `novelKnowledgePhase` 分组逻辑（状态徽标/配色沿用）。

## 组件结构

```
KnowledgePage.tsx（重组）
 ├─ 状态：search / phaseFilter / sort / expandedNovelId / deleteNovelTarget
 ├─ 数据：novels + tasks + stats（getNovelStats 合并为 Map<novelId, NovelStatRecordDto>）
 ├─ 头部 + 统计条（基本不变）
 ├─ 工具栏（新）
 ├─ NovelTable.tsx（新）：列头 + 行
 │    ├─ NovelTableRow.tsx（新）：单行 + 可展开版本明细
 │    └─ 复用 VersionTag.tsx
 └─ DeleteNovelModal.tsx（新）
```

## 布局与信息层级

### 工具栏
| 控件 | 行为 |
|---|---|
| 搜索框 | 按 标题 / novelId 实时过滤（`toLowerCase().includes`） |
| 状态过滤下拉 | 全部 + 处理中/待切分/待向量化/失败/可检索/未知（映射 `novelKnowledgePhase`） |
| 排序 | 更新时间↓（默认）/ 标题 / 版本数 |

### 表格列
| 列 | 内容 |
|---|---|
| 小说 | 标题（主）+ novelId（副，等宽小字）+ 状态徽标（沿用 phase 配色） |
| 版本数 | `splitProfiles` 数（可点击展开） |
| 场景数 / 向量数 | 来自 stats；无数据 `—` |
| 更新时间 | `updatedAt` 格式化 |
| 操作 | 「去处理」（→ `/process?novelId=X`）、「删除」 |

### 可展开行
- 点击行或版本数列展开。
- 展开时拉 `splitProfiles`（`useQuery(['splitProfiles', novelId], enabled: expanded)`），加载中转圈，失败显示错误。
- 版本明细：复用 `VersionTag`（版本标签 + chunk 参数 + 逐版本删除）。

## 操作流

| 操作 | 触发 | 确认 | 行为 |
|---|---|---|---|
| 删除整部小说 | 行尾「删除」 | `DeleteNovelModal`（危险提示 + 「同时删除任务记录」勾选） | `deleteKnowledgeBaseById` + `softDeleteNovel`；成功 toast + 刷新 `['novelSummaries']`（勾选则刷新 `['tasks']`） |
| 删除单个版本 | 展开区版本行「×」 | VersionTag 内联确认（沿用现有） | `deleteVersionByNovelId`；成功 toast + 刷新 `['splitProfiles', novelId]` |
| 运行中任务门控 | — | — | `hasRunningTasks`（PENDING/PROCESSING）时删除按钮禁用 |

## 边界情况

| 场景 | 行为 |
|---|---|
| 搜索无匹配 | 表格区显示「无匹配小说」+ 清空筛选按钮 |
| 过滤后为空 | 表格空态（当前过滤条件下无书），非整页空 |
| 无统计数据 | 场景/向量数列显示 `—` |
| 展开行加载中/失败 | 转圈 / 错误提示 |
| 任务运行中 | 整书 + 版本删除均禁用 |
| 空库 | 保留现有「暂无已登记小说」空态 |

## 错误处理

- 数据加载失败（novels/tasks/stats）：现有错误横幅保留。
- 删除失败：沿用 `getApiErrorMessage` / `handleConflict409` 的 toast 处理。
- 无新增校验。

## 测试

- `npm run build`（tsc 类型校验）+ 浏览器手工 E2E 覆盖：
  1. 搜索（标题/novelId）、状态过滤、排序
  2. 展开行显示版本明细；逐版本删除
  3. 整书删除弹窗（含 purge 勾选）确认流程
  4. 运行中任务时删除禁用
  5. 搜索无匹配 / 空库 / 无统计数据 空态
  6. 与「去处理」深链（`/process?novelId=X`）联动
