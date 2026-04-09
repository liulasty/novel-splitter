# Plan 文档总索引（已整理）

## 1. 用法说明
- 这份文档是 `docs/plan` 的唯一导航入口。
- 先看“主线文档（01~10）”，再看“专项设计 / 历史分析”。
- 所有新计划文档必须先在此登记，否则视为未纳管。

## 2. 主线文档（交付必读）
1. [`01-mobile-ia-and-scope.md`](./main/01-mobile-ia-and-scope.md) - 范围与信息架构
2. [`02-architecture-and-routing.md`](./main/02-architecture-and-routing.md) - 架构与路由
3. [`03-component-and-state-reuse.md`](./main/03-component-and-state-reuse.md) - 组件与状态复用
4. [`04-mobile-network-and-polling.md`](./main/04-mobile-network-and-polling.md) - 网络策略与轮询
5. [`05-upload-experience-and-constraints.md`](./main/05-upload-experience-and-constraints.md) - 上传体验与约束
6. [`06-page-prd-home-ingest.md`](./main/06-page-prd-home-ingest.md) - 首页/入库页 PRD
7. [`07-page-prd-knowledge-chat.md`](./main/07-page-prd-knowledge-chat.md) - 知识库/问答页 PRD
8. [`08-api-contract-and-integration.md`](./main/08-api-contract-and-integration.md) - 接口契约与联调
9. [`09-test-observability-and-acceptance.md`](./main/09-test-observability-and-acceptance.md) - 测试与可观测
10. [`10-rollout-and-risk-control.md`](./main/10-rollout-and-risk-control.md) - 发布与风险控制

## 3. 专项设计（按主题查阅）
- [`20260403_MQ_Ingestion_Design.md`](./special/20260403_MQ_Ingestion_Design.md) - MQ 异步切分/入库设计
- [`implementation_roadmap.md`](./special/implementation_roadmap.md) - 阶段性研发路线图

## 4. 历史分析（参考，不作为当前规范）
- [`engineering-analysis-report.md`](./archive/engineering-analysis-report.md) - 工程现状分析报告

## 5. 推荐阅读顺序（按角色）
- 产品/项目：`01 -> 06 -> 07 -> 08 -> 10`
- 前端：`01 -> 02 -> 03 -> 04 -> 06 -> 07 -> 08 -> 09`
- 后端：`02 -> 04 -> 08 -> 09 -> 20260403_MQ_Ingestion_Design -> implementation_roadmap`
- 测试：`06 -> 07 -> 08 -> 09 -> 10`

## 6. 文档状态看板
| 文档 | 类型 | 状态 | 说明 |
| :--- | :--- | :--- | :--- |
| 01~10 | 主线交付 | Active | 当前版本的执行基线 |
| 20260403_MQ_Ingestion_Design | 专项设计 | Active | 可指导后端异步链路改造 |
| implementation_roadmap | 路线图 | Active | 用于阶段规划与里程碑追踪 |
| engineering-analysis-report | 历史分析 | Reference | 保留背景，不直接作为实施标准 |

## 7. 维护规则（防止再次混乱）
- 命名规则：`NN-topic.md`（主线）或 `YYYYMMDD-topic.md`（专项设计）。
- 每个文档开头必须包含：目标、范围、状态（Draft/Active/Deprecated）、最后更新时间。
- 新文档请基于 [`TEMPLATE.md`](./templates/TEMPLATE.md) 创建。
- 新增专题优先复用已有文档章节，避免“同主题多文件散落”。
- 被替代文档不删除，改为 `Deprecated` 并在本索引写明替代文档。

## 8. 统一术语
- **Mobile**：`/m` 路由下的手机端页面集合。
- **任务终态**：`SUCCESS` 或 `FAILED`。
- **任务状态**：`PENDING`、`PROCESSING`、`SUCCESS`、`FAILED`。
- **小说状态**：`PENDING`、`SPLITTING`、`SPLIT_COMPLETED`、`EMBEDDING`、`COMPLETED`、`FAILED`。

## 9. 更新记录
- v2.0：重构索引结构，按“主线 / 专项 / 历史”分层，新增状态看板与维护规则。
