# Novel Splitter 检查与验收文档

## 1. 文档目标

用于对以下两条核心链路进行统一检查与验收：

- 离线建库链路（切分流水线）
- 在线问答链路（Ask 流水线）

文档产出用于：

- 联调阶段逐项打勾
- 测试阶段验收留痕
- 上线前评审和回归基线

---

## 2. 验收范围

### 2.1 离线建库（切分流水线）

用户上传小说文件
-> `interfaces` `NovelController.triggerPipeline()`
-> `application` `NovelFacadeService.pipeline()`
-> **先落库生成/确定 `novelId`**（`novels.id`，后续全链路以此为主键）
-> `TaskService.createTask()` 写入 `PENDING`（任务元数据包含 `novelId`、`version`）
-> RabbitMQ 异步投递
-> `application` `SplitWorker` 消费
-> `pipeline-core` `LoadNovelUseCase`
-> `pipeline-core` `SplitNovelUseCase`
-> `text-processing`/`validation` 执行
-> `SceneRepository.saveScenes()` 持久化
-> `application` `EmbedWorker` 消费
-> `pipeline-core` `EmbedNovelUseCase`
-> `embedding` `OnnxEmbeddingService`
-> `VectorStore.upsert()`
-> 状态 `EMBED_DONE`

### 2.2 在线问答（Ask 流水线）

用户提问
-> `interfaces` `RagController.ask()`
-> `application` `RagOrchestrationService`
-> `embedding` `OnnxEmbeddingService` 问题向量化
-> `retrieval` `RetrievalService` Top30 粗筛
-> `retrieval` `AnswerPolicyClassifier` 意图分类
-> `embedding` `OnnxRerankerService` Top30 精排
-> Top5 保留
-> `context-assembler` `ContextAssembler` Prompt 组装
-> `llm-client` `RobustLlmClient` 生成答案
-> Controller 返回

---

## 3. 前置环境检查（必须全部通过）

- [ ] PostgreSQL 可用，读写正常
- [ ] RabbitMQ 可用，交换机和队列声明成功
- [ ] ChromaDB 可用，集合可读写
- [ ] ONNX 模型文件可加载（`bge-small-zh-v1.5`、`bge-reranker-base`）
- [ ] `application.yml` 中限流、重试、批处理参数已配置
- [ ] 服务启动日志无致命错误（`ERROR`）

建议记录：

- 服务版本/提交号
- 环境（dev/test/stage）
- 验收执行人和时间

---

## 4. 离线建库链路检查项

## 4.1 接口触发与任务创建

- [ ] 调用 `NovelController.triggerPipeline()` 返回成功
- [ ] 任务表生成一条 `PENDING` 记录
- [ ] 任务元数据完整（`taskId`、`novelId`、`version`）

通过标准：

- 接口返回 2xx
- DB 中任务状态初始为 `PENDING`

## 4.2 MQ 投递与消费

- [ ] 任务消息成功投递到 RabbitMQ
- [ ] `SplitWorker` 正常消费，无堆积
- [ ] 消费失败进入重试逻辑，最终可进入 DLQ

通过标准：

- 队列消息堆积可控
- 失败消息有明确去向（重试或 DLQ）

## 4.3 `LoadNovelUseCase` 检查

- [ ] 能正确读取小说文件
- [ ] 基本元数据解析正确（书名、章节数）
- [ ] `NovelCacheRepository` 写入成功

通过标准：

- 文件读取失败时有明确错误日志
- 元数据字段非空且与输入一致

## 4.4 `SplitNovelUseCase` + 文本处理检查

- [ ] `ChapterRecognizer` 章节边界识别合理
- [ ] `ParagraphSplitter` 分段结果无异常空块
- [ ] `SemanticDensityAnalyzer` 输出可解释
- [ ] `SpeakerModel` 对对话段识别稳定
- [ ] `OverlapChunkingStrategy` 保留重叠文本
- [ ] `SceneValidator` 过滤规则生效

通过标准：

- Scene 长度分布在目标区间（无大量极短/极长）
- 不合法 Scene 被过滤并记录数量

## 4.5 持久化与状态推进

- [ ] `SceneRepository.saveScenes()` 持久化成功
- [ ] 批量写入采用分批 `flush/clear`
- [ ] 状态推进到 `SPLIT_DONE`

通过标准：

- 场景数量与预期误差在可接受范围
- 无长事务超时、无内存异常

## 4.6 向量化入库检查

- [ ] `EmbedWorker` 可批量读取 Scene
- [ ] **入库前已具备 `novelId`（`novels.id`）**，并在本次建库链路中保持不变
- [ ] 限流生效（避免 provider `429`）
- [ ] `OnnxEmbeddingService` 输出 512 维向量
- [ ] Mean Pooling + L2 归一化生效
- [ ] `VectorStore.upsert()` 成功写入 ChromaDB
- [ ] Chroma 元数据包含 `novelId` 与 `version`（可用于按 `novelId` 精确删除/回收）
- [ ] 最终状态为 `EMBED_DONE`

通过标准：

- 维度固定 512
- 向量范数接近 1
- DB 与向量库数量一致性通过抽样校验（同一 `novelId` + `version` 的 scene 数量与向量条目数量一致或可解释）

---

## 5. 在线问答链路检查项

## 5.1 请求入口与编排层

- [ ] `RagController.ask()` 入参校验正确
- [ ] 编排由 `RagOrchestrationService` 执行
- [ ] `retrieval` 不再承担 LLM 编排职责

通过标准：

- 请求链路日志可追踪到完整阶段
- 分层职责符合架构约束

## 5.2 问题向量化与粗筛

- [ ] 问题文本向量化成功（512 维）
- [ ] Chroma HNSW 粗筛返回 Top30
- [ ] 检索延迟可接受

通过标准：

- Top30 返回数量稳定
- 无空向量/维度错误

## 5.3 意图分类

- [ ] `AnswerPolicyClassifier` 可区分支持与拒答场景
- [ ] 非小说问题可走拒答策略

通过标准：

- 误放行率在可接受范围
- 拒答文案符合产品预期

## 5.4 Reranker 精排

- [ ] `OnnxRerankerService` 处理 30 个 pair
- [ ] `sigmoid(logits)` 分数在 `[0,1]`
- [ ] 精排后可稳定取 Top5

通过标准：

- Top5 相关性优于粗筛前序（抽样人工评估）
- 排序结果可复现（同输入基本一致）

## 5.5 Prompt 组装与生成

- [ ] `ContextAssembler` 按 token 预算裁剪
- [ ] 去重与合并策略生效
- [ ] `RobustLlmClient` 限流与指数退避生效
- [ ] 返回答案结构符合接口约定

通过标准：

- 不超 token 预算
- LLM 调用失败可降级并返回可读错误

---

## 6. 模型分工验收矩阵

| 阶段 | bge-small-zh-v1.5 | bge-reranker-base |
|---|---|---|
| 切分建库阶段 | 使用 | 不参与 |
| Ask: 问题向量化 | 使用 | 不参与 |
| Ask: 向量粗筛 Top30 | 使用 | 不参与 |
| Ask: 精排 Top30->Top5 | 不参与 | 使用 |
| Prompt 组装 | 不参与 | 不参与 |
| LLM 生成 | 不参与 | 不参与 |

验收要求：

- [ ] 两模型职责无重叠、无越层调用
- [ ] 推理日志可区分向量模型与重排模型

---

## 7. 非功能验收项

## 7.1 性能

- [ ] 单本中等体量小说建库总时长达标
- [ ] Ask 首字响应/完整响应达标
- [ ] 并发下无明显队列阻塞

## 7.2 稳定性

- [ ] MQ 重试 + DLQ 可验证
- [ ] 外部 API 限流触发时系统可恢复
- [ ] 数据库长事务和锁等待可控

## 7.3 可观测性

- [ ] 每阶段有可检索日志（包含 `taskId`/`requestId`）
- [ ] 关键指标可统计（耗时、失败率、重试次数）

---

## 8. 最终验收结论模板

### 8.1 结论

- [ ] 通过
- [ ] 有条件通过（需修复以下问题）
- [ ] 不通过

### 8.2 阻塞问题

- 问题1：
- 问题2：

### 8.3 风险与后续动作

- 风险1：
- 风险2：
- 负责人：
- 截止时间：

---

## 9. 附录：建议的最小验收样例集

- 样例 A：短篇小说（章节少，验证基础流程）
- 样例 B：长篇小说（章节多，验证批处理与事务）
- 样例 C：高对话密度文本（验证说话人识别和切分）
- 样例 D：跨章节追问（验证 Top30->Top5 重排效果）
- 样例 E：越权/闲聊问题（验证意图拒答）
