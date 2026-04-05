这是一份基于提供的 Spring Boot + React 项目（代码库实际为 React，非你提示中提到的 Vue）生成的系统级工程分析报告。分析结论全部基于仓库中的实际代码结构、依赖配置以及文件内容推导。

## 1. 项目整体架构分析
- **架构模式**：采用 **前后端分离** + **模块化单体架构 (Modular Monolith)**。系统在单个 Spring Boot 进程中运行，但通过 RabbitMQ（如 `LoadWorker`, `SplitWorker` 等）实现了离线耗时任务的异步解耦。
- **分层设计**：后端使用了极具 **DDD（领域驱动设计）** 和 Clean Architecture 风格的分层。包含 `domain`、`repository`、`application`、`infrastructure` 等 12 个 Maven 子模块。`Controller -> Service -> Repository` 分层非常清晰。
- **模块边界是否合理**：合理且清晰。例如 `TaskRepository` 作为防腐层，将 `JpaSplitTaskEntity` (持久化对象) 转换为 `SplitTask` (领域对象)，隔离了基础设施与核心业务逻辑。
- **是否存在“伪分层”或“上帝类”**：`NovelIngestionService` 存在轻微的“上帝类”倾向。它揉杂了本地文件加载、切分器实例化（`SceneAssembler`）、任务状态维护以及 RabbitMQ 消息发送的编排工作，职责略重。

## 2. Spring Boot 后端分析
- **技术栈识别**：Spring Boot 3.2.1, Java 21, PostgreSQL (JPA/Hibernate), RabbitMQ, ChromaDB, 混合大模型 SDK（集成 DeepSeek / Coze / Ollama / Gemini）。
- **依赖合理性**：依赖管理非常规范（使用父 `pom.xml` 的 `<dependencyManagement>` 统一管理版本），且没有发现明显的过时依赖。使用了 `picocli` 支持脚本执行，设计精巧。
- **配置结构**：`application.yml` 规范严谨，充分利用了环境变量占位符（如 `${DB_HOST:localhost}`），具备极好的容器化部署（Docker/K8s）适配能力。
- **事务管理、异常处理**：
  - 异常处理很完善，通过 `@RestControllerAdvice` (`GlobalExceptionHandler`) 捕获并封装为统一的 `ApiResponse.error()`。
  - 事务管理基本达标，在 `TaskService` 等层使用了 `@Transactional`，但在跨数据源（PostgreSQL 与 ChromaDB 向量库）的操作上，缺乏分布式事务一致性保障。
- **是否存在典型反模式**：
  - **无 Mapper 直出逻辑**：Repository 封装良好。
  - **弱鉴权反模式**：安全拦截器存在严重的实现缺陷（详见安全性分析）。

## 3. 前端分析（纠正：实际为 React 项目）
> **缺失信息/纠正**：需求中要求分析 Vue，但分析 `novel-splitter-web/package.json` 发现，这是一个基于 **React 19 + Vite + TypeScript** 的项目。以下基于实际代码分析。
- **技术栈**：React 19, Vite, Zustand 5 (状态管理), React Router 7, TailwindCSS 4, React Query 5, Axios。
- **状态管理**：使用了 `Zustand` (`useAppStore.ts`) 作为轻量全局状态，但代码库中实际更多依赖了 `React Query` 处理服务端数据缓存。
- **路由设计**：使用 `react-router-dom` 实现了 `createBrowserRouter`，结构清晰。但**缺失路由守卫**，没有对 `/system` 或 `/chroma-admin` 等敏感页面做强制前端拦截。
- **API 调用封装是否规范**：**不规范，存在严重缺陷**。`client.ts` 很好地封装了拦截器并注入了 `localStorage` 的 Token，但 `ragApi.ts` 中却独立 `axios.create({ baseURL: '/api/v1' })`，导致 RAG 请求丢失了 Token 注入逻辑和全局错误捕获机制。
- **组件拆分是否合理**：**存在严重“巨型组件”反模式**。`src/pages/` 下的文件过大，如 `ChatPage.tsx` (16KB)、`SystemPage.tsx` (14KB)，UI 渲染、状态绑定和 API 调用严重耦合，缺乏合理的 Hooks 和功能组件拆分。

## 4. 前后端交互设计
- **API 设计风格**：标准 RESTful 风格，如 `POST /api/v1/rag`（提问）和 `GET /api/tasks/{taskId}`（任务查询）。
- **DTO / VO 是否规范**：非常规范。后端通过 `RagRequest` 等 DTO 接收数据，配合 `@Valid` 校验；响应被全局封装在 `ApiResponse<T>` 中。前端的 `types/api.ts` 与之严格对应。
- **错误码体系是否统一**：统一了 JSON 结构，但**缺乏业务错误码枚举**。强依赖 HTTP 状态码（如 400、401、500）与字符串 `message`，不利于前端做精细化多语言或错误跳转处理。
- **鉴权方式**：极简静态 Token 鉴权。Header 携带 `Authorization: Bearer <Token>`，与 `application.yml` 中的静态配置比对，并非 JWT 或 Session。

## 5. 数据流 & 关键链路分析（重点）
**完整调用链推导：RAG 对话链路**
1. **前端页面**：`ChatPage.tsx` 获取用户输入，调用 `ragApi.debug()` 或对应方法。
2. **API**：发起 HTTP POST 请求到后端 `/api/v1/rag`。
3. **Controller**：`RagController.ask()` 接收请求，通过 `@Valid` 校验参数。
4. **Service**：`RagService.ask()` 协调全流程：
   - 拦截：`AnswerPolicyClassifier.classify` 前置拦截非小说意图问题。
   - 查询组装：`RetrievalQueryBuilder` 构造结构化检索条件。
   - 向量检索：调用 `RetrievalService` 查 ChromaDB。
   - 上下文组装：`ContextAssembler` 将匹配的 Scene 组装为 Prompt。
   - LLM调用：`RobustLlmClient` 请求配置的大模型。
5. **返回**：将大模型生成的 `Answer` 同步返回给前端渲染。

- **耦合点**：检索（Chroma）、数据库（Postgres）与大模型推理逻辑强耦合在一个同步方法中。
- **潜在性能瓶颈（致命）**：`RagService` 中的检索与 LLM 调用是**纯同步阻塞调用**。由于 LLM（特别是本地 Ollama）生成时间通常长达几秒到几十秒，这将瞬间耗尽 Tomcat 线程池。
- **可优化点**：必须将该接口重构为流式响应（Server-Sent Events, SSE），目前代码库中虽然有 `TaskSseService` 证明系统有 SSE 能力，但 RAG 链路尚未使用。

## 6. 性能与扩展性分析
- **是否支持高并发**：**不支持**。除了 RAG 接口的同步阻塞外，配置中 `Ollama` 占用了 `num_threads: 16`，单机显存和 CPU 会被单个请求跑满，无法处理并发。
- **是否存在明显瓶颈**：
  - **阻塞调用**：与外部 LLM / Chroma 数据库的网络 I/O 是最大的阻塞点。
  - **大事务风险**：虽然切分入库使用了 RabbitMQ 解耦，但 `LoadWorker` 加载大体积 txt 文件时可能导致 OOM。
- **水平扩展能力**：异步入库系统（基于 RabbitMQ 的 Worker 群）具备极好的水平伸缩（Scale-out）能力，可通过增加消费者节点提升小说入库速度。

## 7. 安全性分析
- **SQL 注入**：安全。全部使用 Spring Data JPA/Hibernate，自动参数化查询，无拼接 SQL 风险。
- **鉴权是否存在绕过风险（严重漏洞）**：
  查看 `AuthInterceptor.java` 源码发现典型的 **Fail-Open（失效即开放）** 漏洞：
  ```java
  if (!StringUtils.hasText(authToken)) {
      return true; // 严重：如果配置漏写，直接全站裸奔！
  }
  ```
  如果生产环境的 `${API_AUTH_TOKEN}` 环境变量未注入，整个 API 将对公网完全开放。
- **敏感信息泄露**：`application.yml` 中集中存放了各类大模型（Coze/DeepSeek/Gemini）的 API Key 配置，存在代码库泄露导致的资损风险（建议强制依赖云端 Secret Manager）。

## 8. 工程质量评估
- **代码规范**：后端极为优秀，接口清晰，应用了防腐层和设计模式；前端规范较差，页面逻辑臃肿。
- **可维护性**：后端 Maven 模块化降低了耦合，可维护性极高。前端需要重构。
- **可测试性**：后端使用 Lombok `@RequiredArgsConstructor` 实现依赖注入，非常易于编写 Mock 单元测试。前端因 UI 和请求强耦合，难以单测。
- **CI/CD 友好性**：非常友好。根目录下配备了完备的 `Dockerfile` 和各类 `docker-compose` 文件，随时可接入 Jenkins / GitHub Actions。

---

## 9. 问题清单（按严重程度排序）

**[严重] AuthInterceptor 存在 Fail-Open 鉴权绕过漏洞**
- **影响**：生产环境若遗漏环境变量配置，后台所有接口（包含 Chroma Admin 和小说入库）将完全对外暴露。
- **原因**：拦截器中判断 `!StringUtils.hasText(authToken)` 时直接返回了 `true`。
- **修改建议**：修改为白名单机制，如果 `authToken` 为空，应直接抛出系统配置异常或返回 401 拦截。

**[严重] 前端 `ragApi.ts` 未注入 Axios 拦截器**
- **影响**：RAG 问答请求无法带上鉴权 Token，一旦后端修复了鉴权逻辑，聊天功能将永久 401 报错。
- **原因**：`ragApi.ts` 自行 `axios.create()` 且未引入 `client.ts` 中的全局拦截器。
- **修改建议**：将 `ragApi.ts` 中的请求改为直接从 `import { apiClient } from './client'` 引入并调用。

**[高] RAG 问答链路为同步阻塞请求**
- **影响**：极低的并发上限（QPS可能 < 5），且前端需要长时间 loading 等待（易触发网关超时）。
- **原因**：`RagService.ask()` 等待 LLM 生成全部文本后才返回 `Answer` 对象。
- **修改建议**：重构 `/api/v1/rag`，使用 Spring WebFlux 的 `SseEmitter` 或 `Flux<String>` 实现大模型的流式字级吐出（Streaming）。

**[中] 前端存在巨型组件反模式**
- **影响**：代码难以维护，二次开发极易引发回归 Bug。
- **原因**：`ChatPage.tsx` 等页面将状态管理、网络请求、DOM 渲染混写在一起（代码 > 15KB）。
- **修改建议**：抽取自定义 Hook（如 `useChatLogic.ts`）剥离业务状态，将 UI 拆分为 `MessageList`, `ChatInput` 等纯渲染组件。

---

## 10. 优化路线图（可执行改造计划）

**阶段 1（1~3天）：高危修复与稳定性补丁**
- [后端] 修复 `AuthInterceptor.java` 拦截逻辑，改为默认拒绝（Fail-Closed）。
- [前端] 修复 `ragApi.ts`，统一使用 `client.ts` 进行请求，确保 Token 正常传递和错误统一拦截。
- [前端] 增加 React Router 鉴权守卫，防止未输入 Token 的用户进入敏感后台页面。

**阶段 2（1~2周）：核心体验与架构重构**
- [后端] 引入 Spring WebFlux 或利用现有的 SSE 基础设施，将 `RagController` 的问答接口改造为流式输出。
- [前端] 配合后端的 SSE 改造 `ChatPage.tsx`，实现类似 ChatGPT 的打字机渲染效果，并在此过程中重构、拆分该巨型组件。
- [后端] 在 `application.yml` 引入更严谨的业务错误码体系（Enum），替代纯文本 message，方便前端处理异常逻辑。

**阶段 3（架构升级）：安全与分布式演进**
- [安全] 引入标准的 JWT 体系或 Spring Security，替代现有的静态 Token 鉴权，实现多用户隔离。
- [数据] 针对 `NovelIngestionService` 中的入库流程，引入分布式事务最终一致性方案（如基于 RabbitMQ 的重试/死信队列和本地消息表），解决 DB 和 ChromaDB 的状态可能不一致问题。
- [运维] 将 LLM Key 抽离至 Vault 等密钥管理系统，禁止通过默认 `application.yml` 硬编码兜底。