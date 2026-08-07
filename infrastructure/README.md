# infrastructure

## 模块概述
作为系统的基础设施层，主要负责提供数据持久化（基于 Spring Data JPA）及底层通用工具（IO、JSON处理），通过实现领域层的仓储接口完成依赖倒置。

## 核心职责
- **实现持久化契约**：实现 `domain` 层定义的各个 Repository 接口（如 `NovelRepository`、`SceneRepository`），完成领域模型与数据库的交互。
- **ORM 与实体映射**：通过 JPA 实体（Entity）映射底层数据库表结构，并利用 MapStruct 实现 JPA 实体与纯领域模型之间的双向转换。
- **提供基础工具**：封装底层的基础设施能力，如文件系统操作（`FileUtils`）和 JSON 序列化/反序列化（`JsonUtils`）。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Spring Data JPA, MapStruct, Jackson Databind, Commons IO, Commons Lang3

## 模块依赖
- 本模块依赖的内部子模块：`domain`
- 依赖本模块的内部子模块：`application`, `interfaces`, `batch-processing`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `JpaNovelEntity` | 实体(JPA) | JPA 实体类，映射底层数据库中的小说表结构，包含特定的持久化注解。 |
| `NovelRepositoryJpaImpl` | 实现类 | 实现领域层 `NovelRepository` 接口，封装基于 Spring Data JPA 的具体数据操作。 |
| `NovelMapper` | 接口(MapStruct) | 定义 JPA 实体（如 `JpaNovelEntity`）与领域模型（如 `Novel`）之间的映射与转换规则。 |
| `FileUtils` | 工具类 | 屏蔽底层文件系统细节，提供统一的本地小说文件读取、写入及路径管理能力。 |

## 扩展点
- **扩展点 1**：可无缝替换底层的数据库访问技术（如切换为 MyBatis-Plus 或 MongoDB），只需提供 `domain` 层 Repository 的新实现即可，完全不影响上层业务。
- **扩展点 2**：`JsonUtils` 可通过调整内部的 `ObjectMapper` 配置来扩展特定格式的序列化规则，或者切换为其他 JSON 引擎。

## 注意事项
- **注意 1**：向外暴露或返回的数据必须是 `domain` 层的纯净模型对象，严禁将 JPA 实体（如 `JpaNovelEntity`）直接传递给其他模块，防止持久化上下文泄露。
- **注意 2**：本层只能包含纯技术实现细节，任何与小说拆分、状态流转相关的业务逻辑都严禁下沉到本层。
