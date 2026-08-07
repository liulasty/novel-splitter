# retrieval

## 模块概述
作为系统的知识检索与问答中枢，实现了完整的 RAG（检索增强生成）流程，负责从向量库中精准召回相关文本片段，组装上下文，并驱动大模型生成结构化的问答结果。

## 核心职责
- **RAG 全流程编排**：提供 `RagFacade` 门面类，统筹“查询向量化 → 向量相似度检索 → 上下文组装 → LLM 提示词生成 → LLM 推理”的完整链路。
- **语义相似度检索**：通过 `RetrievalService` 和 `VectorRetrievalService` 将用户的自然语言提问转换为 Embedding，并在目标小说的集合（Collection）中检索 Top-K 场景。
- **智能意图分类**：通过 `AnswerPolicyClassifier` 和 `RuleBasedPolicyClassifier`，根据问题的复杂度或类型（如事实性、总结性），自动选择最优的大语言模型响应策略。
- **查询与结果适配**：提供 `RagRequest` 和 `RetrievalQueryBuilder`，标准化检索条件（如最小置信度过滤）；通过 `RagDebugResponse` 返回检索诊断信息（上下文统一格式由 context-assembler 模块的组装阶段完成）。

## 技术栈
- 核心语言：Java 21
- 主要依赖：Spring Context, JUnit Jupiter (Test)

## 模块依赖
- 本模块依赖的内部子模块：`domain`, `embedding`, `context-assembler`, `llm-client`
- 依赖本模块的内部子模块：`application`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `RagFacade` | 服务门面 | RAG 链路的对外入口，封装了对 `embedding`、`assembler` 和 `llm-client` 模块的协调调用。 |
| `RetrievalService` | 接口 | 核心检索契约，定义如何根据查询意图从向量库提取高相关性的领域文本（Scene）。 |
| `VectorRetrievalService` | 实现类 | 基于 `EmbeddingService` 和 `VectorStore` 实现的基于语义相似度的具体检索逻辑。 |
| `AnswerPolicyClassifier` | 接口 | 回答策略分类器，决定如何根据不同问题类型选择或定制 LLM 的提示词策略。 |
| `RagProperties` | 配置类 | 读取并映射 `application.yml` 中定义的检索阈值（如 `min-confidence`）和 Top-K 数量。 |

## 使用示例
```java
// 构建带有参数的检索请求
RagRequest request = new RagRequest();
request.setNovelId("novel-1");
request.setQuery("小说的主角叫什么？");
request.setTopK(5);

// 通过门面发起 RAG 流程，直接获取大模型回答及引用出处
String answerJson = ragFacade.chat(request);
```

## 扩展点
- **扩展点 1**：可实现更高级的 `AnswerPolicyClassifier`，引入诸如意图识别大模型（Router LLM）来动态路由请求至不同尺寸的大模型，平衡成本与质量。
- **扩展点 2**：在 `RetrievalService` 层引入混合检索（Hybrid Search，如 BM25 + 向量召回）以提高准确率。

## 注意事项
- **注意 1**：为了支撑前端复杂的引用溯源与 Debug 需求，`RagFacade` 需确保返回结果（如 `RagDebugResponse`）中完整保留所选用片段的 `chunkId` 和 `confidence`。
- **注意 2**：检索阈值（`min-confidence`）配置过高可能导致有效上下文被丢弃，大模型将因为“没有找到答案”而拒绝回答。