package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.chroma.ChromaCollection;
import com.novel.splitter.domain.model.embedding.chroma.ChromaQueryResponse;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ChromaDB 向量数据库的 HTTP API 客户端实现
 * <p>
 * 负责通过 RestClient 与本地或远程的 ChromaDB 服务交互，提供向量及场景块的增删查和 Collection 生命周期管理功能。
 * 它是 NLP/RAG 检索增强生成中重要的基础设施组件，用于存储和检索小说文本片段的高维向量。
 * </p>
 * <p>
 * 激活条件：当配置 {@code embedding.store.type=chroma} 时生效。
 * 线程安全：本组件为无状态 Singleton Bean，内部状态 {@code collectionId} 使用 volatile 及双重检查锁（DCL）保证懒加载的线程安全性。
 * </p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "embedding.store.type", havingValue = "chroma")
public class ChromaVectorStore implements VectorStore {

    private final String collectionName;
    private final RestClient restClient;

    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    private volatile String collectionId;

    /**
     * 构造函数注入依赖
     *
     * @param builder        Spring 容器管理的 RestClient.Builder
     * @param chromaUrl      ChromaDB 服务地址
     * @param collectionName ChromaDB 集合名称
     */
    public ChromaVectorStore(
            RestClient.Builder builder,
            @Value("${chroma.url:http://localhost:8081}") String chromaUrl,
            @Value("${chroma.collection:novel-splitter}") String collectionName) {
        this.collectionName = collectionName;
        this.restClient = builder.baseUrl(chromaUrl).build();
    }

    /**
     * 保存单个场景文本和对应的向量到 ChromaDB
     *
     * @param scene     场景对象
     * @param embedding 对应的特征向量
     */
    @Override
    public void save(Scene scene, float[] embedding) {
        // 将单条数据包装为列表后调用批量保存接口
        saveBatch(Collections.singletonList(scene), Collections.singletonList(embedding));
    }

    /**
     * 批量保存场景文本和对应的向量到 ChromaDB
     *
     * @param scenes     场景对象列表
     * @param embeddings 对应的向量列表
     */
    @Override
    public void saveBatch(List<Scene> scenes, List<float[]> embeddings) {
        // 确保目标 Collection 已经创建
        ensureCollectionExists();

        if (scenes.isEmpty()) return;

        // 提取场景 ID 列表
        List<String> ids = scenes.stream().map(Scene::getId).collect(Collectors.toList());
        // 提取并构建元数据（Metadata）列表，用于后续的条件过滤
        List<Map<String, Object>> metadatas = scenes.stream()
                        .map(s -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("chapter_index", s.getChapterIndex());
                            if (s.getChapterTitle() != null) map.put("chapter_title", s.getChapterTitle());
                            map.put("start_paragraph_index", s.getStartParagraphIndex());
                            
                            if (s.getMetadata() != null) {
                                if (s.getMetadata().getNovel() != null) {
                                    map.put("novelId", s.getMetadata().getNovel());
                                    map.put("novel", s.getMetadata().getNovel()); // Keep for backward compatibility (保留以实现向后兼容)
                                }
                                if (s.getMetadata().getVersion() != null) {
                                    map.put("version", s.getMetadata().getVersion());
                                }
                                if (s.getMetadata().getParentSceneId() != null) {
                                    map.put("parent_scene_id", s.getMetadata().getParentSceneId());
                                }
                                if (s.getMetadata().getChunkType() != null) {
                                    map.put("chunk_type", s.getMetadata().getChunkType());
                                }
                                if (s.getMetadata().getSequenceNum() != null) {
                                    map.put("sequenceNum", s.getMetadata().getSequenceNum());
                                }
                            }
                            return map;
                        })
                        .collect(Collectors.toList());
                
        // 提取原始文档内容列表
        List<String> documents = scenes.stream().map(Scene::getText).collect(Collectors.toList());

        // Convert float[] to List<Double> for Chroma API (将浮点数组转换为 Chroma API 需要的 Double 列表格式)
        List<List<Double>> embeddingsList = embeddings.stream()
                .map(this::toDoubleList)
                .collect(Collectors.toList());

        // 组装 ChromaDB 的添加文档请求体
        Map<String, Object> request = new HashMap<>();
        request.put("ids", ids);
        request.put("embeddings", embeddingsList);
        request.put("metadatas", metadatas);
        request.put("documents", documents);

        log.debug("Saving {} scenes to ChromaDB", scenes.size());

        // 发起 HTTP POST 请求向 ChromaDB 插入数据
        restClient.post()
                .uri(collectionUri("/add"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        
        log.info("Saved {} vectors to ChromaDB collection '{}'", scenes.size(), collectionName);
    }

    /**
     * 根据过滤条件删除 ChromaDB 中的文档
     *
     * @param filter 过滤条件字典
     */
    @Override
    public void delete(Map<String, Object> filter) {
        // 确保目标 Collection 存在
        ensureCollectionExists();
        
        if (filter == null || filter.isEmpty()) {
            // 为避免意外清空数据，空过滤器时不执行删除操作
            log.warn("Delete called with empty filter, ignoring to avoid accidental data loss. Use reset() to clear all.");
            return;
        }

        // 构建符合 ChromaDB 格式的 where 子句
        Map<String, Object> request = new HashMap<>();
        request.put("where", buildWhereClause(filter));

        // 发送删除请求
        restClient.post()
                .uri(collectionUri("/delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        
        log.info("Deleted documents from ChromaDB collection '{}' with filter: {}", collectionName, filter);
    }

    /**
     * 重置 ChromaDB 中的 Collection，删除并重建
     */
    @Override
    public void reset() {
        if (collectionId == null) {
            ensureCollectionExists();
        }
        
        // Delete the collection (删除当前的 Collection)
        try {
            restClient.delete()
                    .uri(collectionUri(null).replace(collectionId, collectionName))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted ChromaDB collection: {}", collectionName);
        } catch (Exception e) {
            log.warn("Failed to delete collection (might not exist): {}", e.getMessage());
        }
        
        // Clear ID to force recreation (清除缓存的 ID，以强制下次操作时重新创建)
        this.collectionId = null;
        ensureCollectionExists();
        log.info("Reset ChromaDB collection: {}", collectionName);
    }

    /**
     * 获取 Collection 中的文档总数
     *
     * @return 文档总数，失败时返回 -1
     */
    @Override
    public long count() {
        ensureCollectionExists();
        try {
            // 获取并返回 Collection 的文档数量
            return restClient.get()
                    .uri(collectionUri("/count"))
                    .retrieve()
                    .body(Long.class);
        } catch (Exception e) {
            log.error("Failed to get count from ChromaDB", e);
            return -1;
        }
    }

    /**
     * 根据查询向量搜索最相似的文档
     *
     * @param queryEmbedding 查询的特征向量
     * @param topK           最大返回结果数
     * @param filter         查询过滤条件
     * @return 包含文档 ID、相似度和元数据的记录列表
     */
    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        ensureCollectionExists();

        // 转换查询向量的类型
        List<Double> embeddingList = toDoubleList(queryEmbedding);

        // 组装查询请求体
        Map<String, Object> request = new HashMap<>();
        request.put("query_embeddings", Collections.singletonList(embeddingList));
        request.put("n_results", topK);
        // We need ids, distances, and metadatas (我们需要返回 ID、距离和元数据)
        request.put("include", Arrays.asList("distances", "metadatas")); 
        
        // 如果提供了过滤条件，则附加 where 子句
        if (filter != null && !filter.isEmpty()) {
            request.put("where", buildWhereClause(filter));
        }

        // 发起查询请求
        ChromaQueryResponse response = restClient.post()
                .uri(collectionUri("/query"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChromaQueryResponse.class);

        // 处理空结果情况
        if (response == null || response.getIds() == null || response.getIds().isEmpty()) {
            return Collections.emptyList();
        }

        // 提取第一组查询（因为我们只传入了一个 query_embedding）的返回结果
        List<String> resultIds = response.getIds().get(0);
        List<Double> distances = response.getDistances().get(0);
        List<Map<String, Object>> resultMetas = (response.getMetadatas() != null && !response.getMetadatas().isEmpty()) ? response.getMetadatas().get(0) : null;

        // 组装并返回最终的 VectorRecord 列表
        return IntStream.range(0, resultIds.size())
                .mapToObj(i -> {
                    Map<String, Object> meta = null;
                    if (resultMetas != null && i < resultMetas.size()) {
                        meta = resultMetas.get(i);
                    }
                    return new VectorRecord(
                            resultIds.get(i),
                            // Convert distance to similarity score approx
                            // ChromaDB default distance is cosine distance which ranges [0, 2]
                            // Score = 1.0 - distance ensures similarity score is proportional and higher is better.
                            // (ChromaDB 默认返回的是余弦距离，范围在 [0, 2] 之间。
                            // 这里使用 1.0 - 距离作为相似度分数，确保分数越高越相似)
                            1.0 - distances.get(i), 
                            meta
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 确保 ChromaDB 中的 Collection 已存在。
     * 采用双重检查锁（DCL）的懒加载策略，确保在多线程并发插入或查询时，不会触发重复的 HTTP 创建请求。
     * 直接尝试 POST 创建 Collection，若抛出已存在（4xx）冲突，再通过 GET 获取现有的 Collection ID，
     * 避免在高负载下遍历全量 Collection 带来的巨大开销。
     */
    private void ensureCollectionExists() {
        // 第一重检查
        if (collectionId != null) return;

        synchronized (this) {
            // 第二重检查
            if (collectionId != null) return;

            try {
                // 直接尝试创建 (POST)
                Map<String, String> request = Collections.singletonMap("name", collectionName);
                ChromaCollection newCollection = restClient.post()
                        .uri("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(ChromaCollection.class);

                if (newCollection != null) {
                    this.collectionId = newCollection.getId();
                    log.info("Created ChromaDB collection: {} ({})", collectionName, collectionId);
                    return;
                }
            } catch (Exception e) {
                log.debug("Failed to create collection, maybe it already exists. Error: {}", e.getMessage());
            }

            // 如果创建失败（比如冲突），尝试通过 GET 获取已有的 Collection 信息
            try {
                ChromaCollection existingCollection = restClient.get()
                        .uri("/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionName)
                        .retrieve()
                        .body(ChromaCollection.class);
                if (existingCollection != null) {
                    this.collectionId = existingCollection.getId();
                    log.info("Found existing ChromaDB collection: {} ({})", collectionName, collectionId);
                } else {
                    throw new RuntimeException("Failed to initialize ChromaDB collection " + collectionName);
                }
            } catch (Exception ex) {
                throw new RuntimeException("Failed to ensure ChromaDB collection exists: " + collectionName, ex);
            }
        }
    }

    /**
     * 将 float 数组转换为 Double 类型的 List，以适配 ChromaDB 的 JSON 序列化需求。
     *
     * @param embedding 原始特征向量数组
     * @return 转换后的 Double 列表
     */
    private List<Double> toDoubleList(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return Collections.emptyList();
        }
        return IntStream.range(0, embedding.length)
                .mapToObj(i -> (double) embedding[i])
                .collect(Collectors.toList());
    }

    /**
     * 拼接基于当前 Collection ID 的 Chroma API URI。
     *
     * @param suffix URI 后缀（如 /add, /query 等）
     * @return 完整的 API 路径
     */
    private String collectionUri(String suffix) {
        String base = "/api/v2/tenants/" + DEFAULT_TENANT
                + "/databases/" + DEFAULT_DATABASE
                + "/collections/" + collectionId;
        if (suffix == null || suffix.isBlank()) {
            return base;
        }
        return base + suffix;
    }

    /**
     * 将业务过滤条件映射转换为 ChromaDB 支持的 Where 查询条件格式。
     *
     * @param filter 原始业务过滤条件映射
     * @return 转换后的 ChromaDB where 条件映射
     */
    private Map<String, Object> buildWhereClause(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return Collections.emptyMap();
        }

        // 将每个条件转换为特定的操作符子句
        List<Map<String, Object>> clauses = filter.entrySet().stream()
                .map(entry -> Map.<String, Object>of(entry.getKey(), buildOperatorClause(entry.getValue())))
                .collect(Collectors.toList());

        // 如果只有一个条件，直接返回；否则使用 $and 逻辑运算符包裹
        if (clauses.size() == 1) {
            return clauses.get(0);
        }
        return Map.of("$and", clauses);
    }

    /**
     * 根据传入的过滤值类型，生成对应的 ChromaDB 操作符子句（例如 $eq, $in 等）。
     *
     * @param value 过滤条件的值
     * @return 包含操作符的查询子句映射
     */
    private Map<String, Object> buildOperatorClause(Object value) {
        if (value == null) {
            return Map.of("$eq", null);
        }
        // 如果已经是嵌套的映射，则直接转换后返回
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return converted;
        }
        // 处理列表类型的 IN 查询
        if (value instanceof List<?> list) {
            return Map.of("$in", list);
        }
        // 处理数组类型的 IN 查询
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return Map.of("$in", list);
        }
        // 默认为相等匹配
        return Map.of("$eq", value);
    }
}
