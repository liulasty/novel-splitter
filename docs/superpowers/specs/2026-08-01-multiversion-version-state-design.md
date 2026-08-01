# 多版本分片状态统一（v1/v2 切分后刷新丢失）设计

日期：2026-08-01
状态：已批准（设计评审通过）

## 问题背景

小说以 v2 参数重新切分后，前端刷新 / 切换页面，版本选择回到硬编码 `v1`，v2 场景已落库却成为无法访问的"逻辑孤岛"。三个根因：

1. **数据面状态漂移**：写入端（SplitWorker）按 `(novelId, version, chunkSize, chunkOverlap)` 分区落库，但读取端点（章节场景预览等）无 version 过滤，跨版本合并返回，UI 读到的不是用户想看的版本。
2. **路由键缺失**：前端 URL 只承载 `novelId`，version 是页面级 `useState("v1")`（`useProcessTask.ts:16`），刷新即丢失；深链无法表达版本。
3. **发现器缺位**：后端已有版本发现接口（`/api/knowledge/id/{novelId}/versions`、`/split-profiles`），但 Process 页不消费它，版本选择器退化为一次性本地变量，跨页面生命周期不感知新生成的资源。

## 目标

- 全站（Process / Chat / RagDebug / Ingest）版本选择统一，URL `?version=` 作为唯一事实源。
- 默认自动发现最新版本；深链 / 刷新 / 跨页后 v2 存活。
- 读取端点按版本过滤，读写分区对齐，v2 不再是不可达孤岛。
- **不引入新表 / 新基础设施**：版本元数据可由 scenes 表派生，发现接口已存在。

## 范围界定

**做**：
- 后端读取端点加 version 过滤；发现器确定性排序。
- 前端共享 `useSplitVersion` hook（URL 驱动 + 发现器）。
- 四个页面（Process / Chat / RagDebug / Ingest）接入统一版本状态。
- `novelApi.getScenes` 加 version 参数。

**不做**（明确排除，理由）：
- `novel_split_versions` 新表 + 回填脚本：冗余。`/split-profiles` 已从 scenes 表 `SELECT DISTINCT` 派生版本目录；生命周期状态已按场景粒度由 `EmbedStatus` 跟踪。
- WebSocket / `history.pushState`：项目已定「Polling, not SSE」（docs/decisions.md），切分完成靠轮询 + react-query 失效即可。
- 告警埋点、空版本兜底大 UI：内部管理工具，本次不做。
- Zustand store：URL 本身即跨页共享状态，无需双源同步。

## 后端改动

### 1. 章节场景读取端点加 version 过滤

`GET /api/novels/{novelId}/chapters/{chapterId}/scenes`（interfaces/.../NovelController.java:142）

- 加可选参数：`@RequestParam(value = "version", required = false) String version`
- 传 version → 仅返回该版本场景；不传 → 保持旧行为（跨版本合并），向后兼容。
- 链路：`NovelFacadeService.getScenesByChapter` → `SceneRepositoryJpaImpl` → `JpaSceneRepository` 新增
  `findByNovelIdAndChapterIdAndVersion(String novelId, Long chapterId, String version, Pageable pageable)`。

### 2. 知识库场景端点同理

`KnowledgeBaseController.java:31-40` 的 `GET /api/knowledge/id/{novelId}/scenes` 与 `GET /api/knowledge/{novelName}/scenes` 加可选 `version` 参数，传则过滤。

### 3. 发现器确定性排序（修复"最新"的判定）

`JpaSceneRepository.findDistinctProfilesByNovelId`（JpaSceneRepository.java:80）当前 `SELECT DISTINCT` 无排序，"最新"不可靠（Chat 页 `length - 1` 假设脆弱）。

改为按版本聚合、以最后写入时刻排序：

```sql
SELECT s.version, s.chunkSize, s.chunkOverlap
FROM JpaSceneEntity s
WHERE s.novel.id = ?1
GROUP BY s.version, s.chunkSize, s.chunkOverlap
ORDER BY MAX(s.id) ASC   -- 旧→新，末位 = 最新生成
```

依据：`JpaSceneEntity.id` 为 `IDENTITY` 自增列；版本重切分（删旧写新）后 `MAX(id)` 反映最后写入时刻，语义上即"最新生成的版本"。

效果：`/split-profiles` 返回 旧→新 有序列表，`profiles[length-1]` = 最新，全站"最新"定义统一。

## 前端改动

### 共享 hook：`useSplitVersion(novelId)`（新建 `src/hooks/useSplitVersion.ts`）

返回：

```ts
interface UseSplitVersionResult {
  version: string;
  setVersion: (v: string) => void;           // 用户显式选择 / 新建版本输入
  profiles: SceneSplitProfileDto[];           // 发现器结果，旧→新，末位=最新
  currentProfile?: SceneSplitProfileDto;      // version 对应的 {chunkSize, chunkOverlap} 元数据
  latestVersion?: string;                     // profiles[length-1]?.version
  isDiscovering: boolean;                     // profiles 查询加载中（门控解析）
  refresh: () => void;                        // invalidate ['splitProfiles', novelId]
}
```

内部模型：

- 数据源：`useQuery(['splitProfiles', novelId], () => knowledgeApi.listSplitProfilesByNovelId(novelId), { enabled: !!novelId })` + `useSearchParams` + `sessionStorage`（键 `kb:version:{novelId}`，按小说隔离）。
- `originRef: 'explicit' | 'auto'` 记录当前 version 来源。

**解析优先级**（novelId 变化 / profiles 加载完成时执行，且 `originRef !== 'explicit'`）：

1. URL `?version=` → 若存在于 profiles（或 profiles 为空）→ 采用，origin=explicit，写 session。
2. `sessionStorage kb:version:{novelId}` → 若存在于 profiles → 采用，origin=explicit，写回 URL + session。
3. 最新 profile（`profiles[length-1]?.version`）→ 采用，origin=auto，**不写 URL**（允许后续自动升级）。
4. 无任何分片 → `"v1"`，origin=auto。

**关键行为**：

- **显式优先**：用户 `setVersion` → 写 URL + session，`originRef='explicit'`，永不覆盖，直到切换小说。
- **URL 合法性校验 + 降级**：URL 携带不在 profiles 中的 version（如手输 `v99`）→ 跳过步骤 1，落到 session / 最新，避免空白页。
- **session 按小说隔离**：键含 novelId，A/B 小说不串位。
- **自动升级**：`originRef='auto'` 且发现器刷新出更新的版本 → 自动切到最新（切分完成 → `refresh()` → 新版本浮现）。
- **切换小说**：`originRef` 重置为 auto、version 清空，待新小说 profiles 加载后重新解析。
- **profiles 加载中**：`isDiscovering` 门控，解析前先返回，避免闪 v1 再跳 v2。

### 页面接入

| 页面 | 改动 |
|---|---|
| **Process** | `useProcessTask` 用 `useSplitVersion(currentNovelId)` 替换 `useState("v1")`；版本输入框保留（支持新建版本），选中已有 profile 时用 `currentProfile` 回填/展示 chunkSize、chunkOverlap（"后续信息传递和显示"）；`SplitPreviewModal` 的 `getScenes` 传 version；切分任务完成后调用 `refresh()` 使新版本浮现 |
| **Chat** | `useChatLogic` 用 hook 替换 `selectedProfileIndex`；按 version 选择，`currentProfile` 提供 chunkSize/chunkOverlap 传给 `chatApi.sendMessage`；下拉标签用 `splitProfileLabel(currentProfile)`（如 `v2 (512/64)`） |
| **RagDebug** | 同上，version 传给 `/v1/rag` |
| **Ingest** | `useIngestTask` 用 hook 替换 `useState("v1")` |

### API 客户端

`novelApi.getScenes(novelId, chapterId, version?, page, size)` 加可选 version 参数，拼到 query string。

## 错误处理与边界

| 场景 | 行为 |
|---|---|
| URL 带不存在的 version（`v99`） | 降级到该小说最新有效版本 |
| 小说尚未切分（profiles 空） | version=`v1`，UI 显示"尚未切分 / 新建版本"提示 |
| 切换小说 | origin 重置，version 清空，重新解析新小说最新版 |
| 切分中的新版本（pending，profiles 暂无） | 用户输入保留（explicit），完成后 `refresh()` 浮现并选中 |
| profiles 加载中 | 不解析（`isDiscovering` 门控），避免 v1 闪烁 |

## 测试

- **后端**：`SceneRepository` 版本过滤查询与发现器排序单测；`NovelController` / `KnowledgeBaseController` 新参数单测（有 version 过滤、无 version 兼容）。
- **前端**：项目无测试框架（package.json 无 vitest/jest），本期不引入；以 `npm run build`（tsc 类型校验）+ 浏览器手工 E2E 覆盖 5 场景：
  1. 刷新页面（URL 带 version 存活）
  2. 跨页面来回跳转（Process ↔ Chat 版本一致）
  3. 手动篡改 URL version（降级到最新）
  4. 新建版本切分完成后自动浮现并选中
  5. 切换小说版本正确重置
