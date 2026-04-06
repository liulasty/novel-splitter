package com.novel.splitter.embedding.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.chroma.ChromaCollection;
import com.novel.splitter.domain.model.embedding.chroma.ChromaQueryResponse;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * ChromaDB 向量数据库的 HTTP API 客户端实现
 * <p>
 * 负责通过 RestClient 与本地或远程的 ChromaDB 服务交互，提供向量及场景块的增删查和 Collection 生命周期管理功能。
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
    private final ObjectMapper objectMapper;

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
        this.objectMapper = new ObjectMapper();
        this.restClient = builder.baseUrl(chromaUrl).build();
    }

    @Override
    public void save(Scene scene, float[] embedding) {
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
        ensureCollectionExists();

        if (scenes.isEmpty()) return;

        List<String> ids = scenes.stream().map(Scene::getId).collect(Collectors.toList());
        List<Map<String, Object>> metadatas = scenes.stream()
                        .map(s -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("chapter_index", s.getChapterIndex());
                            if (s.getChapterTitle() != null) map.put("chapter_title", s.getChapterTitle());
                            map.put("start_paragraph_index", s.getStartParagraphIndex());
                            
                            if (s.getMetadata() != null) {
                                if (s.getMetadata().getNovel() != null) {
                                    map.put("novel", s.getMetadata().getNovel());
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
                            }
                            return map;
                        })
                        .collect(Collectors.toList());
                
        List<String> documents = scenes.stream().map(Scene::getText).collect(Collectors.toList());

        // Convert float[] to List<Double> for Chroma API
        List<List<Double>> embeddingsList = embeddings.stream()
                .map(this::toDoubleList)
                .collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("ids", ids);
        request.put("embeddings", embeddingsList);
        request.put("metadatas", metadatas);
        request.put("documents", documents);

        log.debug("Saving {} scenes to ChromaDB", scenes.size());

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
     * @param filter 过滤条件
     */
    @Override
    public void delete(Map<String, Object> filter) {
        ensureCollectionExists();
        
        if (filter == null || filter.isEmpty()) {
            log.warn("Delete called with empty filter, ignoring to avoid accidental data loss. Use reset() to clear all.");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("where", buildWhereClause(filter));

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
        
        // Delete the collection
        try {
            restClient.delete()
                    .uri(collectionUri(null).replace(collectionId, collectionName))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted ChromaDB collection: {}", collectionName);
        } catch (Exception e) {
            log.warn("Failed to delete collection (might not exist): {}", e.getMessage());
        }
        
        // Clear ID to force recreation
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

        List<Double> embeddingList = toDoubleList(queryEmbedding);

        Map<String, Object> request = new HashMap<>();
        request.put("query_embeddings", Collections.singletonList(embeddingList));
        request.put("n_results", topK);
        // We need ids, distances, and metadatas
        request.put("include", Arrays.asList("distances", "metadatas")); 
        
        if (filter != null && !filter.isEmpty()) {
            request.put("where", buildWhereClause(filter));
        }

        ChromaQueryResponse response = restClient.post()
                .uri(collectionUri("/query"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChromaQueryResponse.class);

        if (response == null || response.getIds() == null || response.getIds().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resultIds = response.getIds().get(0);
        List<Double> distances = response.getDistances().get(0);
        List<Map<String, Object>> resultMetas = (response.getMetadatas() != null && !response.getMetadatas().isEmpty()) ? response.getMetadatas().get(0) : null;

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
        if (collectionId != null) return;

        synchronized (this) {
            if (collectionId != null) return;

            try {
                // 直接尝试创建
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

            // 如果创建失败（比如冲突），尝试通过 GET 获取
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
}
