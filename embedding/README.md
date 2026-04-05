# novel-splitter-embedding

## 模块简介 (Module Introduction)
`embedding` 模块是本系统构建智能检索能力的核心基建，其核心职责是提供高效的文本向量化（Embedding）处理能力，并支持与向量数据库的交互管理。在 RAG（检索增强生成）架构中，该模块是将自然语言转化为机器可理解的高维数学表示的关键桥梁。

### 核心特性
- **本地化推理引擎**：深度整合 `onnxruntime`，支持在本地运行 ONNX 格式的嵌入模型，保障数据隐私，同时支持 CPU 和 CUDA 加速。
- **标准化向量存储**：提供 `VectorStore` 接口，内置两种实现：
  - **内存存储 (InMemoryVectorStore)**：轻量级，支持本地 JSON 文件（`vector_store.json`、`vector_metadata.json`）持久化，适合开发与测试。
  - **Chroma 存储 (ChromaVectorStore)**：对接专业的 Chroma 向量数据库，支持海量数据的高维相似度检索与元数据过滤。
- **无缝集成 Spring Boot**：支持属性配置与 Bean 的自动装配。

## 使用步骤 (Usage Steps)

### 1. 引入依赖
在项目的 `pom.xml` 中引入该模块（依赖 `domain` 模块及 `onnxruntime` 等）：

```xml
<dependency>
    <groupId>com.novel.splitter</groupId>
    <artifactId>embedding</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 环境准备
- **模型文件**：模块内部（`src/main/resources/embedding/`）默认打包了基础的 ONNX 模型和词表。系统启动时会自动解压并加载。如果需要使用自定义模型，可通过配置指定外部路径。
- **ChromaDB（可选）**：如果配置使用 `chroma` 存储类型，请确保目标 Chroma 数据库服务已启动。

## 配置说明 (Configuration Instructions)

在 Spring Boot 的 `application.yml` 或 `application.properties` 中可以进行如下配置：

```yaml
embedding:
  store:
    # 向量存储类型：'memory' (默认) 或 'chroma'
    type: chroma
  onnx:
    # 外部 ONNX 模型路径，若不填则自动解压并使用 classpath 下自带的模型
    model-path: /path/to/custom/model.onnx
    # 推理引擎：CPUExecutionProvider (默认) 或 CUDAExecutionProvider (GPU加速)
    provider: CPUExecutionProvider

chroma:
  # Chroma 数据库服务地址
  url: http://localhost:8081
  # 集合名称
  collection: novel-splitter
```

## 使用示例 (Usage Examples)

### 文本向量化 (Text Embedding)
通过注入 `EmbeddingService`，可将文本列表批量转换为向量：

```java
import com.novel.splitter.embedding.api.EmbeddingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class MyService {

    @Autowired
    private EmbeddingService embeddingService;

    public void processText() {
        List<String> texts = Arrays.asList("你好，世界", "测试文本向量化");
        List<float[]> embeddings = embeddingService.embedBatch(texts);
        
        System.out.println("Generated embeddings size: " + embeddings.size());
    }
}
```

### 向量存储与检索 (Vector Store & Search)
通过注入 `VectorStore`，可以将场景对象及向量持久化，并执行 KNN（K近邻）相似度搜索：

```java
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private EmbeddingService embeddingService;

    public void searchSimilarScenes(String query) {
        // 1. 将查询文本转为向量
        float[] queryEmbedding = embeddingService.embedBatch(List.of(query)).get(0);
        
        // 2. 构建元数据过滤条件（可选）
        Map<String, Object> filter = new HashMap<>();
        filter.put("novel", "novel_name");
        
        // 3. 检索最相似的 Top 5 记录
        List<VectorRecord> results = vectorStore.search(queryEmbedding, 5, filter);
        
        for (VectorRecord record : results) {
            System.out.println("ID: " + record.getId() + ", Score: " + record.getScore());
        }
    }
}
```

## API 文档 (API Documentation)

### `EmbeddingService`
负责将文本序列化并转换为高维向量。
- `List<float[]> embedBatch(List<String> texts)`: 批量文本嵌入。为了优化 ONNX 模型的推理性能，强制使用批处理方式。

### `VectorStore`
负责场景实体及其对应向量的存储、检索与生命周期管理。
- `void save(Scene scene, float[] embedding)`: 保存单个场景及其向量。
- `void saveBatch(List<Scene> scenes, List<float[]> embeddings)`: 批量保存场景与向量（推荐使用）。
- `List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter)`: 相似度检索 (Semantic Search)。支持依据向量距离以及元数据 (Metadata) 进行精确过滤。
- `void delete(Map<String, Object> filter)`: 根据过滤条件删除匹配的向量数据（内置防误删机制：空过滤器将被忽略）。
- `void reset()`: 清空整个存储集合或本地存储数据。
- `long count()`: 获取当前存储的向量总数。
