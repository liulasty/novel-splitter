# 版本化流水线改造设计（复合主键 + 三阶段流程 + 版本激活）

日期：2026-08-03
状态：已批准（设计评审通过，逐节确认）

## 问题背景

当前实现未满足以下架构目标，需要一次系统性改造：

1. **复合主键不完整**：`version` 只存在于 `scenes` / `split_tasks`，`chapters` 与解析文件无版本；`scenes` 按 `(novelId, version, chunkSize, chunkOverlap)` 浮在查询里，无唯一约束。
2. **章节识别是启发式正则**：`RecognitionStrategyType` 只是字符串开关，`ChapterRecognizer` 自动正则匹配，违背「人工选择枚举」的可控性要求。
3. **阶段一非原子**：LoadWorker 边读边写，失败可能留半截章节数据。
4. **阶段二不可真续传**：SplitWorker「先删后写」，重复执行产生副作用，中断后从头重来。
5. **阶段三无原子激活**：Chroma 单集合 `novel-splitter` + metadata 过滤，无法原子切换版本，检索存在脏读窗口。
6. **无超时废弃回收**：停滞的非终态版本无人标记废弃、无人回收存储。

## 目标

- `(novel_id, version_tag)` 复合主键贯穿所有**版本产物**；**公共基准**（原始文件、章节）归属 novel 层共享。
- 章节识别改为**可扩展枚举 + 策略注册表**，入库时由操作者显式指定。
- 三阶段流程：阶段一原子、阶段二幂等可续传、阶段三原子版本激活（零脏读）。
- 多版本逻辑隔离 + 物理隔离；级联删除；超时废弃回收。
- 前端 `/ingest` = 阶段一，`/process` = 阶段二/三。

## 决策记录

### 决策 1：数据迁移 = 开发库清空重建

现有数据与新增的 `novel_version` 表、`chapters` 唯一约束、场景表调整不兼容。采用 **reset-infra-data 后按新 schema 从零开始**，不做兼容迁移。开发库均为测试数据，成本最低且可完整验证新链路。

### 决策 2：范围 = 全部纳入，一次性交付

复合主键 + 章节枚举 + 三阶段 + 级联删除 + 超时回收 + 前端重构全部落地，不分轮次。

### 决策 3：既有决策更替

| 更替 | 旧决策（2026-08-01） | 新决策 | 原因 |
|---|---|---|---|
| `multiversion-version-state-design` | **不建版本表**，版本目录从 `scenes SELECT DISTINCT` 派生 | 新建 `novel_version` 实体，`(novel_id, version_tag)` 复合主键 | 新原则要求版本承载切分参数组合 + 状态/游标快照，派生无法表达 |
| `process-multitab-design` | /process 三 tab（解析/切分/入库） | /process 改为版本实验视图 | 阶段一迁往 /ingest，/process 聚焦多版本实验 |
| `delete-async-design` | 删除异步化（CleanupWorker + AFTER_COMMIT 事件） | **沿用并扩展**至 novel_version 级联 | 机制已存在，仅需扩大覆盖面 |

## 领域模型与复合主键

### 实体分轨

| 层级 | 实体 | 主键 | 版本语义 |
|---|---|---|---|
| 基准（novel 层） | `novel`、`chapter`、`novel_cache`（baseline 文件） | `novel_id` | 无版本——全 novel 唯一章节基准 |
| 版本产物 | `novel_version`、`scene`(chunk)、`split_task`、向量集合 | `(novel_id, version_tag)` | 每个 version_tag 承载一套切分参数组合；embed 编排状态由 `NovelVersion.embedRunId` 表达，不设独立表 |

### 新增聚合根 `NovelVersion`

```java
NovelVersion {
  // 复合主键
  String novelId;
  String versionTag;
  // 切分参数（version_tag 承载的唯一组合）
  SplitStrategy splitStrategy;  // SCENE_BOUNDARY / OVERLAP_CHUNK / SEMANTIC
  Integer chunkSize;
  Integer chunkOverlap;
  // 生命周期状态
  VersionStatus status;  // PENDING → SPLITTING → SPLIT_DONE → EMBEDDING → EMBED_DONE → ACTIVE
                         // 终态: FAILED / ABANDONED
  // 阶段二断点快照（幂等续传）
  Integer splitCursorChapterIndex;  // 已处理到的章节
  Long splitCursorSceneSeq;         // 已产出的最大 scene seq
  String embedRunId;                // 当前向量化编排 ID
  Long embedCursorSceneSeq;         // 已向量化到的 scene seq
  // 阶段三
  String collectionName;            // 该版本专属向量集合名
  Long activatedAt;
  Long abandonedAt;
  Long createdAt;
  Long updatedAt;
}
```

`VersionStatus` 新增 `ACTIVE` / `ABANDONED`（现 `EmbedStatus` / `NovelStatus` 不表达版本级终态）。

### 唯一约束

- `chapters (novel_id, chapter_index)` 唯一
- `novel_version (novel_id, version_tag)` 唯一
- `scene (novel_id, version_tag, seq)` 唯一 —— 幂等续传的落点

### 磁盘文件

- `novel_cache/{novelId}/baseline.json`：基准（清洗文本 + 章节结构），novel 层
- `novel_cache/{novelId}/{versionTag}/chunks.json`：版本产物（chunk 分片）

## 章节识别策略枚举

`RecognitionStrategyType` 扩展并升级为策略注册表（每个枚举值对应策略对象，可编译章节标题匹配规则；LoadWorker 按枚举分发，消除散落 if/else）：

| 枚举 | 匹配格式 |
|---|---|
| `CN_CHAPTER` | 第X章（现 PLAIN） |
| `CN_BACK` | 第X回 |
| `CN_SECTION` | 第X节 |
| `EN_CHAPTER` | Chapter N / CHAPTER N |
| `PROLOGUE` | 序章 / 楔子 / 引子 / 卷头 |
| `VOLUME_CHAPTER` | 卷章混合（现保留） |
| `CUSTOM` | 自定义正则（现保留） |

**放弃启发式自动检测**：不提供 `AUTO` 枚举。

## 三阶段流程

### 阶段一 · 基准解析（原子）

入口：`/ingest` 上传 + 选章节枚举 → 投 LOAD 队列。

1. 全内存完成清洗 + 按枚举策略识别章节（不落库）
2. 单 DB 事务：重解析先清旧基准 → 插入全部 `chapters`
3. 事务提交成功后落盘 `baseline.json`（可重建缓存，写失败不影响基准完整性）

原子性真源是 DB 的 chapters：成功则基准完整可用，失败整体回滚、无残留，novel 回 `PENDING`。

### 阶段二 · 切分 + 向量化（幂等可续传）

1. 提交切分 → 创建/更新 `NovelVersion(SPLITTING)` → 投 SPLIT 队列
2. SplitWorker **按章节顺序逐章切分**，scene 以 `(novelId, versionTag, seq)` 写入；**每处理完一章**把进度写回 `NovelVersion`（splitCursor）快照（章节数远小于场景数，写放大可接受，换取最细续传粒度）
3. 中断恢复：重试时从 splitCursor 继续，已处理区间跳过；`scene(novelId, versionTag, seq)` 唯一约束保证重复执行无副作用
4. 切分完成 → `SPLIT_DONE` → 投 EMBED 队列
5. EmbedWorker 按固定批次（默认 200 scene，可配置）embed → 写向量 → 更新 embedCursor；中断从游标继续，已完成批次跳过（幂等，复用 embedRunId 过滤过期消息 + 版本状态二次校验）
6. 全部完成 → `EMBED_DONE`

### 阶段三 · 原子版本激活（零脏读）

1. 每版本专属集合 `c_{novelId前8}_{version}`（Chroma 集合名 3–63 字符约束，UUID 截断 + 短前缀）
2. 向量全部写入并校验（EMBED_DONE）→ 激活 = 单 DB 事务：`NovelVersion.status → ACTIVE` + 更新 `novel.active_version_tag` 指针
3. 检索服务按 `active_version_tag` 解析集合 → 切换前后查询命中旧或新版本，**绝不读到半成品**
4. 激活失败回滚，旧 ACTIVE 继续生效
5. 版本共存：多个 version 全量并存，仅 ACTIVE 被默认检索；显式传 version 可 A/B 对比

## 向量层改造

- `ChromaVectorStore` 由单集合 + metadata 过滤改为每版本独立集合
- 集合名规范化：`c_{novelId前8}_{version}`（可读 + 合法 + 可溯）
- `VectorManagementService` 增加：创建 / 删除 / 按 novel 列出集合
- 检索侧（`VectorRetrievalService` / RagFacade）统一从 `novel.active_version_tag` 解析集合，不散落硬编码集合名
- 版本间集合零耦合 → 逻辑隔离 + 物理隔离

## 生命周期治理

### 级联删除

- **整书删除**（`softDeleteNovel`）：单 DB 事务删 novel（软删）+ 所有 novel_version + 对应 scene/split_task；向量集合 + 文件走 `CleanupTask`/`CleanupWorker` 异步回收（沿用现有模式，扩展覆盖面到所有版本）
- **单版本删除**（`deleteVersion`）：删该 `(novelId, versionTag)` 的 scenes/任务 + 异步清该版本向量集合与 chunk 文件

### 超时废弃回收

- 新增 `AbandonedVersionScheduler`（复用 `SchedulingConfig`）
  - 周期（默认 30 分钟）扫描 `NovelVersion where status in (SPLITTING, EMBEDDING) AND updatedAt < now - 2h`
  - 标记 `ABANDONED` → 投 CleanupTask 异步回收向量集合 + chunk 文件
- **决策：废弃版本的 scene 记录保留**（便于诊断与修复后重新 embed，不占 Chroma 存储）

## 前端重构

### /ingest = 阶段一

```
IngestPage
├─ UploadPanel            保留：本地上传 / 远程下载
├─ BaselineParsePanel     新增：章节枚举选择 + 原子基准解析
│   ├─ 策略单选（CN_CHAPTER/CN_BACK/CN_SECTION/EN_CHAPTER/PROLOGUE/VOLUME_CHAPTER/CUSTOM）
│   ├─ CUSTOM 时显示正则输入框
│   ├─ 预览解析：识别章节数 + 前 N 章标题（先看后确认）
│   └─ 确认解析：投阶段一任务 → 轮询结果（成功=基准就绪；失败=提示已回滚无残留）
└─ TaskQueueBoard          任务队列看板（下移/弱化）
```

基准就绪的 novel 显示「前往 /process 做版本实验」入口。

### /process = 阶段二/三（版本实验 + 激活）

```
ProcessPage → ProcessingPanel
├─ 小说选择    仅列基准就绪（PARSED）的小说
└─ 版本实验面板
   ├─ 版本列表   该 novel 全部 NovelVersion（versionTag · splitStrategy · chunkSize/overlap · status · 进度 · 激活标记）
   ├─ 新建版本   输入 versionTag（默认 v2/v3…）+ splitStrategy + chunkSize + chunkOverlap
   └─ 版本操作（按状态 gate 显隐）
       PENDING          → 发起切分
       SPLITTING/EMBEDDING → 显示游标进度（已切 X 章 / 已向量化 X 场景），可「续传」
       SPLIT_DONE       → 发起向量化
       EMBED_DONE       → 「激活」（阶段三原子切换，显示当前 ACTIVE 徽标）
       ACTIVE           → 标记「检索中」，可切换回旧版本
       FAILED/ABANDONED → 失败/废弃原因 · 删除版本 · 修复后续传
```

现有 `useSplitVersion`（sessionStorage + URL 驱动版本发现）需适配：版本目录数据源从「scenes 派生 profiles」改为「`GET /novels/{id}/versions` 返回 NovelVersion 列表」。

### 后端新增 API

| 端点 | 作用 |
|---|---|
| `POST /novels/{id}/baseline` | 阶段一：按章节枚举解析基准（原子） |
| `POST /novels/{id}/versions` | 创建 NovelVersion（参数组合） |
| `GET /novels/{id}/versions` | 版本列表含状态/游标进度 |
| `POST /novels/{id}/versions/{v}/split` | 发起/续传切分 |
| `POST /novels/{id}/versions/{v}/embed` | 发起/续传向量化 |
| `POST /novels/{id}/versions/{v}/activate` | 阶段三原子激活 |
| `DELETE /novels/{id}/versions/{v}` | 单版本级联删除 |
| `GET /novels/chapter-strategies` | 章节枚举列表（已有，扩展元数据） |
| `POST /split/preview` | 切分预览（已有） |

前端 `novelApi.ts` 扩展版本方法（或新增 `versionApi.ts`）。

## 错误处理矩阵

| 环节 | 失败行为 | 并发/竞态保护 |
|---|---|---|
| 阶段一 | DB 事务回滚，chapters 无残留，novel 回 PENDING；缓存文件写失败不影响基准 | 基准解析期间禁止同 novel 重复提交（状态 gate） |
| 阶段二切分 | version=FAILED，保留已切分 scene + splitCursor，修复后从游标续传 | 版本级乐观锁（updatedAt）：同一版本并发投递 split/embed 时后到者被拒 |
| 阶段二向量化 | version=FAILED，保留已向量化批次 + embedCursor，续传跳过已完成批次 | 沿用 embedRunId 过滤过期消息 + 版本状态二次校验 |
| 阶段三激活 | 事务失败回滚，旧 ACTIVE 继续生效；激活前校验：EMBED_DONE 且集合存在且向量计数与 scene 数一致 | 检索读指针单事务，天然无竞态 |
| 检索侧 | 指针指向集合被删（级联/回收后）→ 降级返回空结果 + 告警日志，不抛 500 | — |

## 测试策略

| 层级 | 用例 |
|---|---|
| 单元（text-processing/domain） | 策略注册表各枚举识别用例（第X章/第X回/Chapter N/卷章…）；游标续传纯逻辑；集合名规范化 |
| 集成（application） | 阶段一原子：解析中途抛异常→chapters 无残留、novel 回 PENDING；重解析整包替换 |
| | 阶段二幂等：同一版本连续跑两次 split→scene 计数不变；模拟中断→从 splitCursor 续传→场景数正确 |
| | 阶段三原子：激活前检索命中旧版本→激活后命中新版本→激活失败回滚后仍命中旧版本 |
| 前端 | 手动验证：枚举选择联动正则框、版本操作 gate 显隐、激活徽标、续传进度展示（沿用 tsc + 手工 E2E，不引入测试框架） |

## 范围界定

**做**：全部上述内容。

**不做**（明确排除）：
- 数据兼容迁移（决策 1：清空重建）
- 前端测试框架引入（沿用 tsc + 手工 E2E）
- `/knowledge`、`/chat`、`/rag-debug` 页面的结构性改造（仅受版本目录数据源变化影响，最小适配）
- SSE 流式 Chat（docs/todo.md 既有待办，与本设计无关）
