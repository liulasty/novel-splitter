# domain

## 模块概述
作为整个小说拆分与处理系统的核心领域层，定义了最纯粹的业务模型、仓储契约和核心策略，是所有其他模块的基石。

## 核心职责
- **定义核心业务模型**：提供小说（Novel）、章节（Chapter）、场景（Scene）等核心业务实体与值对象的定义。
- **声明仓储接口**：定义数据访问契约（如 `NovelRepository`），由基础设施层（infrastructure）实现，严格遵循依赖倒置原则。
- **规范任务流转模型**：定义小说拆分（SplitTask）、清理（CleanupTask）等异步任务的领域模型及状态枚举。
- **制定领域策略接口**：提供文本分块（ChunkingStrategy）等核心业务策略的抽象接口。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Jackson, Lombok, Jakarta Validation
- 无外部依赖：本模块不依赖任何 Spring、JPA 或底层中间件，保持绝对的业务纯粹性。

## 模块依赖
- 本模块依赖的内部子模块：无（处于系统依赖链的最底层）
- 依赖本模块的内部子模块：所有其他业务及处理模块（如 `infrastructure`, `application`, `batch-processing`, `text-processing` 等）

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `Novel` / `Scene` | 实体 | 定义小说及其拆分后的核心文本场景模型，包含关键业务属性与元数据。 |
| `NovelRepository` | 接口 | 定义小说实体的持久化、查询及原始文件读取的仓储契约。 |
| `SplitTask` | 实体 | 描述小说解析、拆分、向量化入库等处理任务的流转状态。 |
| `ChunkingStrategy` | 接口 | 定义将长文本切分为适合 LLM 及向量库处理的文本块策略。 |

## 使用示例
```java
// 使用领域模型及策略
Novel novel = new Novel();
novel.setTitle("示例小说");
novel.setStatus(NovelStatus.PENDING);

// 策略接口调用示例
ChunkingStrategy strategy = new OverlapChunkingStrategy(500, 100);
List<ContextBlock> blocks = strategy.chunk(rawText);
```

## 扩展点
- **扩展点 1**：实现 `ChunkingStrategy` 接口，可自由添加基于语义或特定语法规则的新型文本分块策略。
- **扩展点 2**：通过扩展或新增 `repository` 下的接口，可无缝定义新的存储契约，由基础设施层灵活对接不同数据库。

## 注意事项
- **注意 1**：本模块处于洋葱架构的最核心，绝对不允许引入任何 Spring、JPA、数据库驱动或具体中间件依赖。
- **注意 2**：所有核心业务规则（如状态流转逻辑、实体固有行为）应尽量封装在领域实体内部，避免产生贫血模型。
- **注意 3**：领域层只关注“业务是什么”，任何关于“如何保存”、“如何调用”的技术细节均需通过接口抽象。