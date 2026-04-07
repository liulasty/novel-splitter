# batch-processing

## 模块概述
作为本项目的核心工作流调度与批处理引擎，负责编排小说从原始文本加载、文本切割、语义校验到向量化入库的完整生命周期（Pipeline）。

## 核心职责
- **定义工作流抽象**：通过 `PipelineContext` 维护整个处理过程中的上下文状态，并通过 `Stage` 接口定义流水线上的独立处理阶段。
- **编排拆分用例**：通过 `SplitNovelUseCase` 等核心编排器，组合 `text-processing`（文本引擎）和 `validation`（校验规则），将小说解析为标准化的场景片段。
- **编排向量化入库用例**：通过 `EmbedNovelUseCase` 编排器，调用 `embedding` 引擎对验证通过的场景进行批量向量化并存入 Chroma 库。
- **ETL 接入点**：提供 `LocalNovelLoader` 等 ETL 组件，支持从本地文件系统高效加载小说原始文本到批处理管线中。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Spring Context, Spring Boot Starter

## 模块依赖
- 本模块依赖的内部子模块：`domain`, `text-processing`, `embedding`, `validation`, `infrastructure`
- 依赖本模块的内部子模块：`application`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `PipelineContext` | 实体 | 流水线上下文对象，在各 Stage 之间传递小说元数据、当前处理进度与中间产物。 |
| `Stage` | 接口 | 流水线阶段契约，要求实现类提供单一职责的处理逻辑（如“解析章节”或“分块”）。 |
| `LoadNovelUseCase` | 用例服务 | 编排底层加载逻辑，从文件或存储系统中将小说原始文本读入内存并初始化上下文。 |
| `SplitNovelUseCase` | 用例服务 | 调度文本处理引擎，执行章节识别与场景切分，并调用校验模块确保数据合法性。 |
| `EmbedNovelUseCase` | 用例服务 | 接收拆分后的场景列表，调度向量化引擎生成 Embedding 并执行批量入库。 |
| `LocalNovelLoader` | ETL类 | 负责读取本地 `root-path` 目录下的 TXT/MD 文件。 |

## 扩展点
- **扩展点 1**：实现新的 `Stage` 接口，可向流水线中无缝插入新的处理逻辑（如“敏感词过滤”或“命名实体识别（NER）抽取”）。
- **扩展点 2**：除了 `LocalNovelLoader`，可以增加诸如 `S3NovelLoader` 等新的 ETL 组件，支持从云存储加载原始小说文件。

## 注意事项
- **注意 1**：批处理流水线（Pipeline）必须保证幂等性，支持在处理失败后（如网络中断）从断点安全重试，避免重复生成相同的向量记录。
- **注意 2**：对于动辄数百万字的长篇小说，需严格控制 `PipelineContext` 中的对象数量与生命周期，防止发生 OOM（内存溢出）。