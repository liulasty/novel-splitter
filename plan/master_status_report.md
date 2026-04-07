# Master 分支开发进度与后续计划分析报告

## 1. 概览
本次分析基于当前 `master` 分支的最新代码，对比了前期规划的所有文档（包括 `plan1.md`、`plan2.md` 以及架构演进与执行报告等）。从结果来看，**绝大部分 P0 和 P1 级别的重构和前端功能均已在 `master` 分支中落地**，系统架构已经成功从原本的“同步直连模式”升级为“基于 MQ 解耦的异步多 Worker 模式”。

## 2. 已完成的工作 (Completed on `master`)

### 2.1 后端底层与架构重构（对应 `plan2.md` 核心任务）
- **数据模型重构**：成功引入了 JPA 实体体系（`JpaNovelEntity`, `JpaChapterEntity`, `JpaSceneEntity`, `JpaSplitTaskEntity`），建立了清晰的数据库关联关系，实现了逻辑软删除机制（`is_deleted`）。
- **领域服务剥离**：完成了 `NovelService`, `ChapterService`, `TaskService` 等核心应用层服务的解耦，尤其是 `TaskService` 彻底隔离了 SPLIT 和 EMBED 的进度状态独立计算。
- **MQ 解耦与 Worker 改造**：
  - 移除了消息体中对本地 `fileName` 的强依赖，统一采用 `novelId` 进行路由。
  - `SplitWorker` 现已成功切断强耦合，处理完切分后只做数据落盘和状态更新，不再硬编码直接发送向量化请求。
  - `EmbedWorker` 现已重构为根据 `novelId` 分页从 `scenes` 表读取场景片段进行批量向量化入库（ChromaDB）。

### 2.2 接口层与前端交互改造（对应 `plan1.md` 核心任务）
- **RESTful API 升级**：`NovelController` 中成功暴露了全新的、更规范的端点：
  - `POST /api/v1/novels/upload`（处理上传并返回 ID）
  - `POST /api/v1/novels/{novelId}/split`（触发分章切分）
  - `POST /api/v1/novels/{novelId}/embed`（触发向量化入库）
  - `GET /api/v1/stats/dashboard`（系统全局统计数据）
  - `GET /api/v1/system/health/models`（模型健康状态探针）
- **前端页面闭环**：
  - **Pipeline 入库页 (IngestPage)**：修复了任务新建表单，打通了“上传 -> 切分 -> 向量化”三段式手动触发流，实时进度卡片 (SSE) 监控已生效。
  - **Dashboard 统计展示**：接入了真实的 `/stats/dashboard` 和健康检查接口，不再是前端 Mock 数据。
  - **知识库管理页 (KnowledgePage)**：补全了 "+ 新增小说" 的路由跳转和已入库文档的展示。

---

## 3. 尚未完成的工作 (Pending / To-Do)

尽管基础重构已完成，但针对**“智能 RAG 检索深度强化”**及部分**用户体验优化**的任务仍处于缺失或待完善状态：

### 3.1 异步语义增强链路（AI Enrichment）—— 最核心缺失
- **现状**：目前 `SplitWorker` 结束后，并未自动触发任何 AI 摘要与语义提取动作。`EmbedWorker` 也是直接拿原始文本 (`scene.getText()`) 喂给 Embedding 模型。
- **待做任务**：
  - 引入 `EnrichWorker` 并打通 MQ 自动流转 (`Split -> Enrich -> Embed`)。
  - 开发 `EnrichNovelUseCase`：调用廉价大模型提取每个 Scene 的 50 字前情摘要、核心人物、地点等。
  - 修改 `EmbedWorker`：将向量化的内容改为 `"摘要：" + summary + "\n正文：" + content` 以大幅提升检索权重和准度。

### 3.2 进阶检索与问答体验优化
- **精排阶段 (Rerank)**：`context-assembler` 预留了 `SceneReScorer`，但暂未接入真正的交叉编码器 (Cross-Encoder) 对初筛 Top-10 结果做二次打分。
- **流式问答输出 (SSE)**：当前 `RagController.ask` 依然是**同步阻塞**返回。前端的 `ChatPage` 也没有实现基于 SSE 的“打字机”流式效果，导致提问时用户需长时间干等。
- **对话引用卡片强化**：聊天页面虽然能回答，但尚未完全利用后端传递的 Citation (来源章节、置信度) 来渲染详细的引证溯源卡片。

### 3.3 之前主动搁置的低优先级功能
- 获取历史会话列表接口 (`ChatHistory`)。
- 向量列表高级过滤查询接口 (`Chroma Admin` 页面的高阶检索过滤)。

---

## 4. 后续演进建议
1. **短期冲刺**：集中精力拿下 **AI 语义增强 (Enrichment)** 和 **Embed 阶段文本拼接**，这是 RAG 系统能“变聪明”的质变点。
2. **中期体验**：将 `Chat` 接口重构为流式响应，解决问答过程中的长时阻塞和请求超时问题。
3. **长期优化**：引入 Rerank 机制，并完成之前搁置的历史会话等业务管理功能。