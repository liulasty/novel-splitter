# embedding

## 模块概述
作为核心向量化引擎，负责将文本数据转换为多维语义向量（Embedding），并提供与向量数据库（ChromaDB 等）集成的读写能力。

## 核心职责
- **文本向量化**：通过 `EmbeddingService` 将文本场景计算为浮点数向量表示，支持基于本地模型（ONNX）的离线推理。
- **Token化与词表管理**：提供基于词表的 `Tokenizer`，将原始字符串转化为模型可接受的 Token 序列输入（如 `TokenizedInput`）。
- **向量库抽象与集成**：提供 `VectorStore` 接口，并实现了内存（`InMemoryVectorStore`）及生产级向量库（`ChromaVectorStore`）的存储与检索。
- **模型加载与推理管理**：利用 `onnxruntime` 管理深度学习模型的生命周期和硬件提供者（CPU/GPU）的选择。

## 技术栈
- 核心语言：Java 21
- 主要依赖：ONNX Runtime (`onnxruntime`), Spring Boot Starter, Spring Web, Jackson, Lombok

## 模块依赖
- 本模块依赖的内部子模块：`domain`
- 依赖本模块的内部子模块：`application`, `batch-processing`, `retrieval`, `text-processing`

## 核心组件
| 组件名称 | 类型 | 核心职责 |
|----------|------|----------|
| `EmbeddingService` | 接口 | 向量生成契约，提供将单个或批量文本转换为多维浮点数数组的能力。 |
| `OnnxEmbeddingService` | 实现类 | 基于 ONNX Runtime 实现的本地离线向量化服务，调用 `OnnxModelHolder` 进行推理。 |
| `Tokenizer` | 工具类 | 负责根据 `Vocabulary` 将文本编码为模型输入特征（input_ids, attention_mask）。 |
| `VectorStore` | 接口 | 定义向量数据写入、删除以及相似度召回搜索的标准契约。 |
| `ChromaVectorStore` | 实现类 | 基于 ChromaDB HTTP API 的向量存储实现，用于持久化知识库。 |

## 使用示例
```java
// 生成文本向量
List<String> texts = Arrays.asList("第一段文本", "第二段文本");
List<float[]> embeddings = embeddingService.embedBatch(texts);

// 写入向量数据库
vectorStore.save("novel-1", chunks, embeddings);

// 基于相似度进行召回检索
List<SearchResult> results = vectorStore.search("novel-1", queryEmbedding, 5);
```

## 扩展点
- **扩展点 1**：实现新的 `EmbeddingService` 接入 OpenAI、DashScope 等在线大模型的 Embedding API。
- **扩展点 2**：实现新的 `VectorStore` 接入 Qdrant、Milvus 或 PostgreSQL(pgvector) 等其他向量数据库。

## 注意事项
- **注意 1**：本地推理的 ONNX 模型文件 (`model.onnx`) 及其词表 (`vocab.txt`) 默认打包在 `src/main/resources/embedding` 下，启动时需注意内存占用。
- **注意 2**：在并发批量处理时，务必正确管理 ONNX 推理会话的线程安全性与内存释放。