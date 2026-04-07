# context-assembler

## 模块概述
作为 RAG 链路的关键枢纽，负责将向量数据库召回的离散知识片段（Scene）进行去重、合并、重排序以及精确的 Token 预算控制，最终组装成大语言模型（LLM）易于理解的上下文。

## 核心职责
- **上下文拼接与合并**：将多个相关的上下文块（`ContextBlock`）按章节、段落顺序进行合并组装，提供 `StandardContextAssembler` 标准组装器。
- **阶段性处理流**：提供 `SceneDeduplicator`（去重）、`SceneMerger`（相邻合并）、`SceneReScorer`（重排序）等多个处理阶段（Stage），优化上下文连贯性。
- **Token 预算管理**：实现 `TokenCounter` 接口（如 `SimpleTokenCounter`）和 `TokenBudgetAllocator` 阶段，严格控制组装后的总 Token 长度，防止超出 LLM 的上下文窗口。
- **配置与参数化**：通过 `AssemblerConfig` 结合 `application.yml` 中的参数（如最大片段数、最大 Token 数）动态控制组装行为。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Spring Boot Starter, Lombok
- 无外部依赖：以纯净的 Java 逻辑为主，依赖 Spring 管理 Bean 生命周期。

## 模块依赖
- 本模块依赖的内部子模块：`domain`
- 依赖本模块的内部子模块：`application`, `retrieval`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `ContextAssembler` | 接口 | 核心契约，定义了将一系列召回文本块组装为单个合并上下文字符串的能力。 |
| `StandardContextAssembler` | 实现类 | 默认的上下文组装流水线，依次执行去重、排序、合并及 Token 截断阶段。 |
| `TokenCounter` | 接口 | 定义评估文本占用 Token 数量的规范。 |
| `TokenBudgetAllocator` | 处理阶段 | 依据预设的 `max-context-tokens` 预算，剔除多余或低优先级的召回片段。 |
| `SceneMerger` | 处理阶段 | 识别并合并物理上相邻的文本场景，还原小说段落连贯性。 |

## 使用示例
```java
// 组装召回的上下文块
List<ContextBlock> retrievedBlocks = getFromVectorStore();
String promptContext = contextAssembler.assemble(retrievedBlocks, maxTokens);

// 此时 promptContext 已经过去重、合并和长度截断，可安全发送给 LLM
```

## 扩展点
- **扩展点 1**：可实现更复杂的 `TokenCounter`（如基于 JTokkit 的精确 BPE Token 计数器）替换现有的简单字数估算（`SimpleTokenCounter`）。
- **扩展点 2**：可新增处理阶段（如添加基于 BM25 或 CrossEncoder 的 `SceneReScorer`），并注入到组装流水线中以提高上下文质量。

## 注意事项
- **注意 1**：组装器必须保证召回片段的顺序（如按原书的物理顺序排序），否则会导致 LLM 生成回答时产生幻觉或逻辑错乱。
- **注意 2**：`TokenBudgetAllocator` 必须是流水线的最后一道关卡，确保严格遵循大模型 Token 上限，避免触发 API 调用异常。