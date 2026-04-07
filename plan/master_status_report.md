# Master 分支开发进度与后续计划分析报告

## 1. 概览
本次分析基于当前 `master` 分支的最新代码，对比了前期规划的所有文档（包括 `plan1.md`、`plan2.md` 以及架构演进与执行报告等）。从结果来看，**绝大部分 P0 和 P1 级别的重构和前端功能均已在 `master` 分支中落地**，系统架构已经成功从原本的“同步直连模式”升级为“基于 MQ 解耦的异步多 Worker 模式”。近期合并的最新特性也进一步增强了系统的健壮性和数据流转的完整性。

## 2. 已完成的工作 (Completed on `master`)

### 2.1 后端底层与架构重构（对应 `plan2.md` 核心任务）
- **数据模型重构**：成功引入了 JPA 实体体系（`JpaNovelEntity`, `JpaChapterEntity`, `JpaSceneEntity`, `JpaSplitTaskEntity`），建立了清晰的数据库关联关系，实现了逻辑软删除机制（`is_deleted`）。
- **领域服务剥离**：完成了 `NovelService`, `ChapterService`, `TaskService` 等核心应用层服务的解耦，尤其是 `TaskService` 彻底隔离了 SPLIT 和 EMBED 的进度状态独立计算。
- **MQ 解耦与 Worker 改造**：
  - 移除了消息体中对本地 `fileName` 的强依赖，统一采用 `novelId` 进行路由。
  - `SplitWorker` 现已成功切断强耦合，处理完切分后只做数据落盘和状态更新，不再硬编码直接发送向量化请求。
  - `EmbedWorker` 现已重构为根据 `novelId` 分页从 `scenes` 表读取场景片段进行批量向量化入库（ChromaDB）。

### 2.2 最新合并特性增强 (Recent Enhancements)
- **文件防重与MD5校验**：`JpaNovelEntity` 引入了 `fileMd5` 和 `fileSize` 字段，系统现在能够在上传阶段基于 MD5 拦截并跳过重复小说的创建。
- **多版本与全局排序**：
  - 章节数据（`Chapter`）引入了 `version` 字段，支持同一小说的多版本数据流转和查询过滤。
  - 场景片段（`Scene`）引入了 `chunkIndex`，确保了切分后在全局检索和组装时的绝对顺序。
- **向量化状态闭环**：`EmbedWorker` 批量入库后，现在会捕获底层向量数据库（如 ChromaDB）返回的 `vectorId`，并更新对应场景片段的 `embedStatus`（SUCCESS/FAILED）和 `vectorId`，形成了严密的数据对齐与状态闭环。

### 2.3 接口层与前端交互改造（对应 `plan1.md` 核心任务）
- **RESTful API 升级**：`NovelController` 中成功暴露了全新的、更规范的端点：
  - `POST /api/v1/novels/upload`（处理上传并返回 ID）
  - `POST /api/v1/novels/{novelId}/split`（触发分章切分）
  - `POST /api/v1/novels/{novelId}/embed`（触发向量化入库）
  - `GET /api/v1/stats/dashboard`（系统全局统计数据）
  - `GET /api/v1/system/health/models`（模型健康状态探针）
  - 获取章节接口已支持 `version` 参数筛选。
- **前端页面闭环**：
  - **Pipeline 入库页 (IngestPage)**：修复了任务新建表单，打通了“上传 -> 切分 -> 向量化”三段式手动触发流，实时进度卡片 (SSE) 监控已生效。
  - **Dashboard 统计展示**：接入了真实的 `/stats/dashboard` 和健康检查接口，不再是前端 Mock 数据。
  - **知识库管理页 (KnowledgePage)**：补全了 "+ 新增小说" 的路由跳转和已入库文档的展示。

---

## 3. 尚未完成的工作 (Pending / To-Do)

尽管基础重构与数据流转闭环已完成，但针对**“智能 RAG 检索深度强化”**及部分**用户体验优化**的任务仍处于缺失或待完善状态：

### 3.1 异步语义增强链路（AI Enrichment）—— 最核心缺失
- **现状**：目前 `SplitWorker` 结束后，并未自动触发任何 AI 摘要与语义提取动作。`EmbedWorker` 也是直接拿原始文本 (`scene.getText()`) 喂给 Embedding 模型，这在复杂小说检索中效果有限。
- **缺失点**：
  - 缺少 `EnrichWorker` 节点。
  - 缺少对 Scene 进行 50 字前情摘要、核心人物、地点等要素的提取。
  - 向量化前缺少将“摘要+正文”拼接的逻辑。

### 3.2 进阶检索与问答体验优化
- **精排阶段 (Rerank)**：`context-assembler` 预留了 `SceneReScorer`，但暂未接入真正的交叉编码器 (Cross-Encoder) 对初筛 Top-10 结果做二次打分。
- **流式问答输出 (SSE)**：当前 `RagController.ask` 依然是**同步阻塞**返回。前端的 `ChatPage` 也没有实现基于 SSE 的“打字机”流式效果，导致提问时用户需长时间干等。
- **对话引用卡片强化**：聊天页面虽然能回答，但尚未完全利用后端传递的 Citation (来源章节、置信度) 来渲染详细的引证溯源卡片。

### 3.3 之前主动搁置的低优先级功能
- 获取历史会话列表接口 (`ChatHistory`)。
- 向量列表高级过滤查询接口 (`Chroma Admin` 页面的高阶检索过滤)。

---

## 4. 详细的后续优先级开发计划

为了解决上述缺失并进一步提升系统的 RAG 能力和用户体验，制定以下优先级的详细开发计划：

### 阶段一：短期冲刺（P0 优先级）—— RAG 质量与体验质变
**目标**：提升 RAG 检索的准确度，并解决问答阻塞等待的体验痛点。

1. **实现 AI 语义增强（Enrichment）链路**
   - **Task 1**: 引入 `EnrichWorker`，订阅新的 MQ Topic（如 `novel.enrich`），打通 `Split -> Enrich -> Embed` 的自动流转。
   - **Task 2**: 开发 `EnrichNovelUseCase`，集成大模型（如 GPT-3.5/GLM-4-Flash），对每个 `Scene` 提取核心人物、地点、50字摘要，并更新到 `JpaSceneEntity` 的 `metadataJson` 中。
   - **Task 3**: 修改 `EmbedWorker`，在调用 Embedding 模型前，将 `metadata` 中的摘要与 `scene.getText()` 进行拼接（例如 `"摘要：" + summary + "\n正文：" + content`），再进行向量化入库以提升命中率。

2. **实现流式问答输出（SSE）**
   - **Task 1**: 重构后端 `RagController.ask` 和 `RagFacade`，底层对接大模型的 Streaming API（基于 Spring AI 或原生 Flux/SSE 返回）。
   - **Task 2**: 前端 `ChatPage` 改造，接入 EventSource 或 fetch 流式读取机制，实现大模型输出时的“打字机”效果，降低用户等待焦虑。

### 阶段二：中期演进（P1 优先级）—— 检索精度与溯源强化
**目标**：进一步提高复杂问题下的回答质量，并让用户清楚知道答案的来源。

1. **接入 Cross-Encoder 精排机制（Rerank）**
   - **Task 1**: 在 `context-assembler` 模块的 `SceneReScorer` 中，集成真实的 Rerank 模型服务（如 BGE-Reranker）。
   - **Task 2**: 对 ChromaDB 初筛返回的 Top-10/20 结果，结合用户问题进行二次交叉打分，提取最相关的 Top-3~5 作为 Context 喂给大模型。

2. **前端对话引用卡片（Citation）渲染**
   - **Task 1**: 确保后端问答接口返回或流式结束后，附带检索到的 `Scene` 来源信息（如小说名、章节名、chunkIndex、相似度分数）。
   - **Task 2**: 前端 `ChatPage` 在气泡下方渲染“参考来源”卡片，点击可展示或预览关联的原文片段。

### 阶段三：长期完善（P2 优先级）—— 业务闭环与管理
**目标**：补齐边缘业务功能，完善后台管理能力。

1. **会话历史管理（Chat History）**
   - **Task 1**: 设计并创建 `JpaChatSessionEntity` 和 `JpaChatMessageEntity` 实体模型。
   - **Task 2**: 提供新建会话、获取会话列表、获取历史消息记录的 RESTful API。
   - **Task 3**: 前端侧边栏接入历史会话列表，支持无缝切换不同的对话上下文。

2. **知识库高级管理（Chroma Admin）**
   - **Task 1**: 提供基于 `version`、`embedStatus` 或 `chunkIndex` 的高阶查询 API，展示切分片段的详细向量化情况。
   - **Task 2**: 前端 KnowledgePage 增加“分块详情”抽屉或页面，支持查看具体片段的内容、状态，或重新触发失败片段的 Embedding 任务。