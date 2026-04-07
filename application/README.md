# application

## 模块概述
作为业务应用层，是系统功能的门面与编排中心。它承接来自接口层的外部请求，协调底层各个领域服务与引擎，并管理基于 RabbitMQ 的异步任务队列。

## 核心职责
- **应用服务编排**：通过 `NovelFacadeService` 等 Facade 类，将 `domain`、`batch-processing` 等模块的原子能力组合为完整的用户用例。
- **数据传输对象映射**：定义了丰富的 DTO（如 `NovelStatRecordDto`、`SplitTaskDto`），并使用 MapStruct (`DtoMapper`) 在应用 DTO 与领域模型之间进行安全转换。
- **异步任务消费与发布**：集成 Spring AMQP，提供 `SplitWorker`、`EmbedWorker` 等后台消费者，处理耗时的拆分、向量化和清理任务。
- **配置与环境管理**：维护 `application.yml` 核心配置文件，并利用 `dotenv-java` 等机制管理全局环境变量及服务注入配置（如 `AppConfig`、`RabbitConfig`）。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Spring Boot Starter AMQP, MapStruct, Dotenv, Lombok

## 模块依赖
- 本模块依赖的内部子模块：`domain`, `batch-processing`, `novelDownloader`, `embedding`, `retrieval`, `context-assembler`, `llm-client`
- 依赖本模块的内部子模块：`interfaces`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `NovelFacadeService` | 服务类 | 小说业务门面，编排小说上传、解析、删除等完整用例。 |
| `SplitWorker` | MQ消费者 | 监听小说拆分队列，异步调度 `batch-processing` 执行分块与验证流程。 |
| `TaskSseService` | 服务类 | 利用 Server-Sent Events (SSE) 向前端推送实时异步任务进度。 |
| `DtoMapper` | 接口(MapStruct) | 提供统一的 `Domain <-> DTO` 映射机制，隔离内部模型与外部 API 数据结构。 |
| `SystemSettingsService` | 服务类 | 读取、管理并向前端暴露系统的当前配置信息（如 LLM 提供商、切分规则）。 |

## 使用示例
```java
// 典型的应用层用例调用（在 Controller 中）
@Autowired
private NovelFacadeService novelFacadeService;

// 上传并触发异步处理流程
Novel novel = novelFacadeService.uploadAndProcess(multipartFile, "小说名");

// 查询任务进度状态DTO
SplitTaskDto taskDto = taskService.getTaskStatus(novel.getId());
```

## 扩展点
- **扩展点 1**：如果系统规模扩大，可将 `Worker`（消费者）类分离部署为独立微服务，实现解耦和弹性伸缩。
- **扩展点 2**：在 `AppConfig` 中添加新的配置 Beans，轻松集成如 Redis（用于缓存小说元数据或分布式锁）等新组件。

## 注意事项
- **注意 1**：`application` 模块是唯一允许直接包含 `application.yml` 的地方，所有环境配置必须收敛于此，避免各模块配置碎片化。
- **注意 2**：应用服务只负责“编排”逻辑（即决定先调A再调B），绝不能包含核心的领域规则计算（如如何分段、状态如何校验）。