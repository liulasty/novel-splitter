**Revising the Plan**

\n\n

I've been going over the user's requirements, focusing on the backend recommendations. My current thinking is centered around how to best translate those into a prioritized task list. I'm starting to see a clear path for structuring the development guidelines. I'm weighing different approaches to the development plan, considering Agile methodologies.


**Structuring the Breakdown**

\n\n

My focus is shifting to the task list's construction. I'm prioritizing database schema design, which feels like the essential foundation. The service layer implementation also feels crucial, and will be next. I'm figuring out how to balance decoupling the MQ with more immediate API adjustments.


**Mapping the Phases**

\n\n

My current thinking is focused on phase breakdown. I've begun to define the sprint structure, starting with database setup and repository definition. The subsequent sprints will progressively tackle the service layer refactoring and the MQ modifications, I need to finalize the integration points between backend and frontend. I'm considering potential testing strategies, along with the expected time.


**Detailing the Breakdown**

\n\n

My current thinking is focused on refining the task breakdown into a set of actionable deliverables. I'm assigning specific priorities, from the crucial database schema (P0) to frontend updates (P4). Next, I'm working through each section, detailing the coding standards, along with the MQ updates. Finally, I need to map out the timelines.



基于前期的后端重构建议（将入库、切分、向量化彻底解耦，建立完整的小说生命周期管理），我为您整理了**优先级的任务排序**、**开发规范**以及**详细的开发计划**。这份报告可直接用于技术方案评审与研发排期。

---

### 一、 开发任务优先级排序 (Task Backlog & Priorities)

遵循“自底向上，先核心后外围”的原则，将重构任务分为四个优先级（P0 - P3）。

#### P0 级：数据模型与底层基建（基础底座，必须最先完成）
*   **[Task-01] 数据库表结构设计与建表**
    *   设计并创建 `novels` (小说主表)、`chapters` (章节树表) 表结构。
    *   修改 `scenes` 表，添加 `novel_id`、`chapter_id` 等外键关联字段。
    *   修改 `split_tasks` 表，增加 `task_type` (枚举：SPLIT/EMBED) 和 `novel_id` (关联主表)。
*   **[Task-02] JPA 实体类 (Entity) 与 Repository 改造**
    *   新增 `JpaNovelEntity`、`JpaChapterEntity` 及其对应的 Repository 接口。
    *   更新现有的 `JpaSceneEntity` 与 `JpaSplitTaskEntity`，维护好 JPA 关联关系（`@ManyToOne`, `@OneToMany` 等，建议采用懒加载）。

#### P1 级：核心领域服务层改造（业务逻辑骨架）
*   **[Task-03] 新增小说领域服务 (NovelService & ChapterService)**
    *   实现小说上传记录落盘、状态机流转（待切分 -> 切分中 -> 已切分 -> 入库中 -> 入库完成）的方法。
    *   实现章节树的批量插入与查询逻辑。
*   **[Task-04] 异步任务进度管理重构**
    *   重写 `TaskService`，隔离 `SPLIT` 任务和 `EMBED` 任务的进度计算规则（各自独立计算 0-100%），不再使用 0-65% 代表切分、65-100% 代表入库的硬编码逻辑。

#### P2 级：消息队列与 Worker 消费者解耦（斩断强耦合）
*   **[Task-05] MQ 消息体 (Message Payload) 重构**
    *   将 `SplitTaskMessage` 和 `EmbedTaskMessage` 中的 `fileName` 依赖彻底移除，统一使用数据库的主键 `novelId` 进行流转。
*   **[Task-06] Worker 消费者逻辑拆分与阻断**
    *   修改 `LoadWorker` 和 `SplitWorker`：切分完成后，仅持久化 `chapters` 和 `scenes` 数据，调用 `NovelService` 更新小说状态为“已切分”，**严格禁止自动发送消息到 embed 队列**。
    *   修改 `EmbedWorker`：调整为根据 `novelId` 从 `scenes` 表分页拉取数据，并调用大模型进行向量化，完成后更新小说状态为“入库完成”。

#### P3 级：API 接口层与前端对接（暴露服务）
*   **[Task-07] Controller 层接口重构**
    *   重构 `POST /api/novels/upload`：保存文件并插入 `novels` 表，返回 `novelId`。
    *   新增 `POST /api/novels/{novelId}/split`：触发分章切分异步任务。
    *   修改 `POST /api/novels/ingest` 为 `POST /api/novels/{novelId}/embed`：触发向量化入库异步任务。
    *   新增查询接口：提供获取小说章节树、获取某章节下所有切分片段（Scene）的接口，供前端预览。
*   **[Task-08] 前端页面适配（联调）**
    *   对接新的 API 结构，配合完成“上传 -> 预览切分结果 -> 确认向量化”的三段式交互。

---

### 二、 后端开发规范 (Development Guidelines)

为确保重构过程的代码质量和稳定性，团队需遵守以下开发规范：

#### 1. 数据库与 JPA 规范
*   **物理外键 vs 逻辑外键**：不强制使用数据库物理外键约束（FK），推荐使用逻辑外键以提升性能，但在 JPA Entity 层需通过 `@JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))` 映射关系。
*   **软删除机制**：所有核心表必须包含 `is_deleted` 字段，配合 Hibernate `@SQLRestriction("is_deleted = false")` 实现全局逻辑删除。
*   **事务控制**：Service 层涉及多表写操作的方法必须加 `@Transactional`。异步 Worker 中的大批量数据写入（如 `scenes` 插入）应控制 Batch Size（如每次 500 条）以防 OOM 或长事务锁表。

#### 2. API 设计与 RESTful 规范
*   **资源导向**：API 路径严格遵循 RESTful，如 `/api/novels`（合集）、`/api/novels/{id}`（单体）、`/api/novels/{id}/chapters`（子资源）。
*   **动词作为子资源**：触发异步任务的接口使用动作子路径，如 `/api/novels/{id}/split` 和 `/api/novels/{id}/embed`。
*   **统一返回体**：严格使用已有的 `ApiResponse<T>` 包装类，包含 `code`, `message`, `data`。异常情况必须由 `GlobalExceptionHandler` 统一拦截并返回标准错误码。

#### 3. 消息队列 (RabbitMQ) 规范
*   **幂等性消费**：Worker 消费消息时，必须先通过 `taskId` 或 `novelId` 查询任务当前状态。若状态已是 `SUCCESS` 或 `PROCESSING`（且无超时），则直接 ACK 或丢弃，防止重复消费。
*   **异常重试与死信队列 (DLQ)**：Worker 抛出异常时，应当控制重试次数（如 3 次），超过次数将消息路由至死信队列，并在数据库中将任务状态置为 `FAILED`，记录 `message`（错误栈信息）。

---

### 三、 开发计划排期 (Development Plan)

假设投入 **1 名后端开发工程师**，本次重构预计周期为 **1 个 Sprint（约 8 个工作日）**。

#### Phase 1：数据底座构建（Day 1 - Day 2）
*   **目标**：完成底层数据库表和 JPA Entity 的建立，确保能进行基础的 CRUD。
*   **具体工作**：
    *   编写 Flyway/Liquibase SQL 迁移脚本或手动 DDL 建表（`novels`, `chapters`）。
    *   完成 Entity 类的映射（包括字段、枚举、关联关系配置）。
    *   编写对应的 Spring Data JPA Repository 接口。
*   **产出物**：数据库结构更新，底层代码可编译。

#### Phase 2：核心逻辑与 MQ 解耦（Day 3 - Day 5）
*   **目标**：彻底切断原本一站式跑到底的流水线，实现“切分”与“向量化”的业务剥离。
*   **具体工作**：
    *   完成 `NovelService` 和 `ChapterService` 核心逻辑。
    *   重构 `LoadWorker` 和 `SplitWorker`，让其输出终点变为数据库 `chapters` 和 `scenes` 表，并在此处中止（不发下一阶段 MQ）。
    *   重构 `EmbedWorker`，改为从数据库读数据并发起向量化。
    *   重构 `TaskService` 的进度计算逻辑。
*   **产出物**：后端通过单元测试可独立完成“仅切分”和“仅向量化”两套流程。

#### Phase 3：API 暴露与联调准备（Day 6 - Day 7）
*   **目标**：提供干净、标准的 RESTful API 供前端调用。
*   **具体工作**：
    *   重构 `NovelController`，废弃旧的 `ingest` 接口。
    *   编写 Swagger/OpenAPI 注解，更新 API 文档。
    *   提供本地 Postman / cURL 测试脚本集。
    *   开始与前端进行接口联调。
*   **产出物**：完整的 API 接口、联调环境。

#### Phase 4：集成测试与收尾（Day 8）
*   **目标**：确保全链路数据一致性、异常容错能力。
*   **具体工作**：
    *   进行端到端 (E2E) 测试：上传 -> 触发切分 -> 查看数据库章节树 -> 触发入库 -> ChromaDB 数据校验。
    *   容错测试：模拟大模型 API 宕机，检查 `EmbedWorker` 是否能正确标记任务失败，并且不影响原小说的 `scenes` 数据。
    *   清理冗余的旧代码（如不再使用的缓存类、旧 DTO）。
*   **产出物**：可交付测试验证的 Release Candidate Release (RC) 预发布版本，合并至此重构工作闭环。