# 预埋能力激活设计：SceneMetadata 全量消费 + prefixContext 启用

- 日期：2026-08-06
- 状态：已对齐，待实现

## 背景与问题

`SceneMetadata`（16 字段）和 `prefixContext` 在系统里属于「基础设施已落地、字段已落库，但主检索/组装链路未接入」的预埋能力，当前处于闲置或半闲置状态。按闲置程度分为 4 类：

1. **语义结构化字段**（`characters` / `role` / `location` / `time` / `extra`）：Schema 全量预留、消费逻辑已写，但生产端固定写 null，整条能力链是死代码。`SceneReScorer.calculateEntityScore` 读 `metadata.getCharacters()`，因 characters 恒为 null 而永远返回 0。
2. **质量评分字段**（`densityScore` / `qualityScore`）：计算逻辑已实现并真实落库，但全链路无消费。
3. **prefixContext 前缀上下文**：切分、落库、DTO 暴露全通，但向量化、检索、组装三环节均未使用。
4. **位置序号字段**（`sequenceNum` / `chapterIndex` / `startParagraph`）：已用于排序/合并/去重，但未做「召回后相邻块自动扩展」。

## 目标与非目标

**目标**：将 4 类预埋能力激活，提升 RAG 检索质量与结果可读性。分三阶段推进，每阶段独立可上线、可回滚。

**非目标**：
- 不做 version bump 的版本灰度（见 2B 边界）。
- 不做自然语言自动解析角色/地点/时间（属 NLP 子项目，留作后续扩展）。
- 不新建 LLM 抽取专用接口（复用 `RobustLlmClient` + `Prompt`）。
- 不为 4 个能力预建统一抽象层（各自简单，YAGNI）。

## 关键发现：enrich 语义抽取管道已预建 90%

`EnrichTaskMessage`、MQ 队列 `novel.task.enrich` + DLQ、binding "enrich"、`sendEnrich`、`SplitWorker` 切分后发送逻辑（`SplitWorker.java:167-174`，受 `splitter.enrich.enabled` 开关控制）**全部已存在**。缺的只有消费端 `EnrichWorker` + LLM 抽取逻辑。Phase 2A 无需新建管道。

## 总体方案：三阶段

| Phase | 内容 | 成本 | 触发方式 |
|---|---|---|---|
| 1 | 质量软加权 + 相邻块扩展 + prefixContext 组装补缝 | 零重嵌入、零 LLM | 配置开关，运行时生效 |
| 2A | `EnrichWorker` LLM 抽取（填真数据） | 每 scene 一次 LLM 调用 | 新上传自动 / 已有小说 `re-enrich` 端点 |
| 2B | 向量输入拼 prefixContext + Chroma 结构化键，同版本重嵌入 | 重嵌入计算 | 复用现有 embed 端点 |
| 3 | role 打通 + 结构化过滤 + 前端标签 | 检索/前端代码 | 安全门打开后生效 |

---

## Phase 1：运行时增强层

只改检索/组装代码，不动已落库数据。三块内容均可配置开关，改完即生效、可回滚。

### 1.1 质量软加权（`SceneReScorer`）

`SceneReScorer` 两条打分路径各加 quality 混合项：

- ONNX 重排路径（`:71-73`）：`final = rerankScore × (1-w) + qualityScore × w`
- 启发式路径（`:88`）：`final = 0.5×向量 + 0.2×关键词 + 0.2×实体 + 0.1×质量 - 长度惩罚`

配置：`AssemblerConfig` 新增 `qualityScoreWeight`（默认 0.15），请求可覆盖。

防护：
- `qualityScore == SCORE_NOT_COMPUTED`（`SceneQualityScoreWriter.java:39` 空白文本哨兵值）时跳过混合，保持原分数。
- 合并后的 scene 沿用首块 qualityScore，文档注明该取舍（`SceneMerger.createMergedScene` 已搬运 `src.getQualityScore()`，`SceneMerger.java:126`）。

**选软加权而非硬过滤**：qualityScore 本质是长度/完整度启发式（`SceneQualityScoreWriter.java:42-58`），过短对话会被误伤。混入加权让其自然下沉但不丢失。

### 1.2 相邻块扩展（新增 `SceneExpander` stage）

新增 `SceneExpander`，插入组装链：`rescore → **expand** → dedup → merge → budget`。

**stage 顺序**：放 rescore 之后。若放之前，重排器会给未命中的邻居打低分，被 dedup/budget 丢弃。放之后，邻居继承锚点分数 × 衰减系数（`anchorScore × 0.9^距离`），可存活。

**锚点**：`Scene.seq`（`Scene.java:82`，(novelId, version) 内单调连续的全局序号）。

**新仓库方法**：
```java
List<Scene> findByProfileAndSeqRange(
    String novelId, String version, int chunkSize, int chunkOverlap,
    long fromSeq, long toSeq);
```
chunk 分区参数从命中场景的 `metadata.chunkSize/chunkOverlap` 取（不跨切分分区）。

配置：
- `assembler.expand-radius`（默认 1，即 ±1 个邻居；`-1` 关闭该特性）
- `assembler.expand-across-chapters`（默认 false）

总量由下游 `TokenBudgetAllocator` 兜底，不会爆 token。

### 1.3 prefixContext 组装补缝

- `ContextBlock` 新增 `prefixContext` 字段。
- `StandardContextAssembler` 组装时，对每个「孤立块」（与上一块 seq 不连续、且未被 merge 吞掉）把 `prefixContext` 带到 ContextBlock。相邻/合并块已有上文，前缀冗余，跳过。
- LlmClient 侧 prompt 序列化时，对带 prefixContext 的块先拼 `[上文接续]\n{prefix}\n[正文]\n{text}`（分隔符集中定义）。

**不用拼进 content**：`validateCitations`（`RagOrchestrationService.java:193-216`）会把 `block.getContent()` 回填进引用卡片，拼进去会污染引用。独立字段由 prompt 序列化消费，引用保持干净。

---

## Phase 2A：Enrichment（LLM 抽取，数据生产）

### 新增 `EnrichWorker`（`application/worker/`，仿 `EmbedWorker` 模式）

- `@RabbitListener(queues = RabbitConfig.ENRICH_TASK_QUEUE)` 消费 `EnrichTaskMessage`。
- `sceneRepository.findByIds(sceneIds)` 加载场景，按 `chapterIndex` 分组。
- 每章一批调 `RobustLlmClient`（已有重试+熔断），prompt 要求：给定章内场景文本，返回每个 scene 的 JSON：`characters[]` / `location` / `time` / `role`（场景功能：`dialogue` / `narration` / `action` / `transition` 之一）。
- **prompt 强制绑定 JSON Schema 输出约束**，复用 `JsonUtils`（infrastructure）解析。
- 写入 `SceneMetadata` 四个字段，**严格限定不触碰其他字段**，保证幂等重跑安全。
- 落库：`SceneRepository` 新增 `updateScenesMetadata(List<Scene>)`（现有仅幂等 insert，缺 update 路径）。

### 失败与幂等

- 某章 LLM 失败 → 记日志、字段留 null、不阻塞后续章、不失败整个任务。解析失败同样降级留 null，不抛异常。
- 重复消息 → 覆盖四个字段，其余不动。
- DLQ 接毒消息（`RabbitConfig.ENRICH_TASK_DLQ` 已绑定）。

### 接入

- 新上传：`splitter.enrich.enabled=true`，`SplitWorker.java:167` 现有发送逻辑直接生效（fire-and-forget，填充 PG）。
- 已有小说：新增 admin 端点 `POST /api/novels/{novelId}/re-enrich` → `findAllByNovelIdAndVersion` 收集 sceneIds → 发 `EnrichTaskMessage`。

---

## Phase 2B：Re-embed（向量化输入升级，数据消费升级）

### embedding 输入拼 prefixContext

`EmbedNovelUseCase.embedBatch` 中，当 `splitter.embedding.use-prefix-context=true` 时，送入 `embeddingService` 的文本改为 `prefixContext + SEP + text`（prefix 为 null 则回退纯 text）。Chroma `documents` 仍存 `Scene::getText` 不变——检索向量带连贯性，返回文本保持干净。

### Chroma metadata 增加结构化键

`buildChromaMetadata`（`ChromaVectorStore.java:168-199`）在字段已填充时追加 `role` / `location` / `time` / `characters`（list）。`validateChromaSceneMetadata` 的必填校验**不动**——这些是可选键，缺失即跳过，不影响写入。

### 同版本重嵌入、不做 version bump（已确认）

- **版本隔离前提不成立**：本次仅调整输入文本 + 补充可选 metadata，chunkSize/chunkOverlap、正文、seq、sceneId 全未变，属同批次数据质量升级，无需物理隔离。
- **幂等性已核实**：embed 流程按 sceneId upsert 覆盖 + 断点续传，重跑不产生重复数据、不残留浮空向量。
- **唯一风险——短暂向量一致性窗口**：逐批覆盖期间集合内新旧向量并存，排序有轻微波动。缓解：仅管理员触发、可低峰期执行、秒级到分钟级窗口对内部工具可接受。
- **审计**：重嵌入前日志记录本次 `embedRunId` 与操作人，异常可基于 runId 排查，不额外开发回滚能力。
- 触发：复用现有 `POST /api/novels/{novelId}/embed`（`NovelController.java:204`）。顺序上先 2A 再 2B，确定性达成。

---

## Phase 3：结构化检索 + 前端展示

### 3.1 role 打通：查询意图 → 场景功能过滤

现状：`RetrievalQueryBuilder.java:89-96` 在问题含「他说了什么」时置 `RetrievalQuery.role = "dialogue"`（查询意图）；Phase 2 后 `SceneMetadata.role` 为场景功能。二者目前仅名字相同、未打通。

改动：`VectorRetrievalService` 构建 where 时，当 `query.getRole()` 命中合法场景功能值时，追加 Chroma 过滤 `role == <值>`。

- 新增 `META_ROLE = "role"` 常量（与 2B 写入键对齐）。
- **安全门**：`retrieval.role-filter.enabled`（默认 false，admin 在 Phase 2 完成后打开）。原因：Chroma 对缺失字段的 where 返回空集，若 enrich 未跑，`role` 键不存在会静默吞掉全部召回。
- 命名冲突处理：不重命名（波及面大），加注释 + 显式映射方法 `mapQueryRoleToSceneFunction`。

### 3.2 结构化过滤：角色 / 地点 / 时间

目标能力：`只检索角色X出场的场景` / `按地点筛选情节` / `按时间线召回`。

- `RetrievalQuery` 新增可选过滤字段：`characterFilter` / `locationFilter` / `timeFilter`。
- `RagRequest`（API 入参）加同样三字段，供管理员/调试页显式传。
- `VectorRetrievalService` 转 Chroma where：
  - 角色：`characters`（list）→ `$contains`（Chroma 字符串数组用 contains）
  - 地点/时间：`location` / `time`（string）→ `$eq`
- 同样受 `retrieval.structured-filter.enabled`（默认 false）安全门约束，空安全。

边界：**不做自然语言自动解析角色/地点/时间**——需角色表/实体库，属 NLP 子项目，留作后续扩展。Phase 3 只做显式结构化条件的通道。

### 3.3 前端展示：结构化标签

- `StandardContextAssembler` 组装 metadata 时把 `characters` / `location` / `role` 一并放入块 metadata（`:77-92` 现仅 novelName/chapterTitle/chapterIndex/mergedSceneIds）。
- `CitationItem.tsx`：引用卡片在章节名下追加人物 / 地点 / 场景类型标签（有值才显示）。
- `RagDebugPage.tsx`：检索结果已读 `sceneMetadata`（`:144`），补结构化字段展示。
- `novelApi.ts`：metadata 类型补字段。
- **附带收益**：`SceneReScorer.calculateEntityScore`（`:137-148`）读 `metadata.getCharacters()`，Phase 2 填真数据后死代码自动复活，无需改逻辑，补测试证明生效。

---

## 配置汇总

| 配置键 | 默认 | 阶段 | 作用 |
|---|---|---|---|
| `assembler.quality-score-weight` | 0.15 | 1 | 质量软加权混合权重 |
| `assembler.expand-radius` | 1 | 1 | 相邻块扩展半径，-1 关闭 |
| `assembler.expand-across-chapters` | false | 1 | 扩展是否允许跨章 |
| `splitter.enrich.enabled` | false | 2A | 切分后自动发 enrich 消息 |
| `splitter.embedding.use-prefix-context` | false | 2B | embedding 输入拼接 prefixContext |
| `retrieval.role-filter.enabled` | false | 3 | 打开 role → Chroma 过滤 |
| `retrieval.structured-filter.enabled` | false | 3 | 打开角色/地点/时间过滤 |

## 测试策略

- **Phase 1**：`SceneExpanderTest`（repo mock 邻居、衰减分数、跨章开关）；`SceneReScorerTest`（quality 混合、SCORE_NOT_COMPUTED 跳过）；prompt 序列化测试（prefixContext 拼串、连续块不拼）；现有 `StandardContextAssemblerTest` 保持通过（新 stage 默认关闭时行为不变）。
- **Phase 2A**：`EnrichWorkerTest`（mock LLM 合法/非法/失败 JSON → 字段写入/留 null/跳过该章）；`EnrichWorkerResumeTest`（重复消息幂等覆盖）。
- **Phase 2B**：`EmbedNovelUseCaseTest`（use-prefix-context 开关下送入 embedding 的文本拼接正确、关时不变）；Chroma metadata 结构化键存在时写入、缺失时跳过、必填校验仍拦缺 sceneId。
- **Phase 3**：`VectorRetrievalServiceTest`（role/character/location/time 各 where 构建、安全门关闭不影响现有检索、enrich 未跑时返回空但不报错）；`SceneReScorerTest`（characters 有值时实体命中生效）；前端 CitationItem 有/无标签两种渲染。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| 重嵌入期间新旧向量并存，排序波动 | 仅管理员触发、低峰期执行、窗口秒级到分钟级 |
| role 过滤在 enrich 未跑时静默返回空 | `retrieval.role-filter.enabled` 安全门默认关闭 |
| 新上传时 enrich 与 embed 并发，首次 embed 可能缺结构化键 | 接受基线；admin 重嵌入（2A→2B）确定性补齐 |
| LLM 抽取失败/超时 | RobustLlmClient 重试熔断；逐章降级留 null 不阻塞 |

## 范围外（后续扩展）

- 自然语言自动解析角色/地点/时间（NLP 子项目）。
- version bump 灰度 + 原子切换（面向用户的升级场景）。
- 知识库列表 / 场景详情抽屉的结构化标签展示。
- `densityScore` 消费（Phase 1 已混入 quality 路径；density 是否参与过滤留待质量分有语义后评估）。
