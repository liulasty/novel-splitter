package com.novel.splitter.embedding.store;

import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.chroma.ChromaCollection;
import com.novel.splitter.domain.model.embedding.chroma.ChromaQueryResponse;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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

    /** 与 Chroma 建表 metadata 一致；与 {@link com.novel.splitter.retrieval.impl.VectorRetrievalService} 检索假设一致 */
    public static final String CHROMA_HNSW_SPACE_KEY = "hnsw:space";

    private final String collectionName;
    private final RestClient restClient;
    private final String hnswSpace;
    private final boolean failOnDistanceMismatch;
    private final boolean eagerInit;
    private final int deleteMaxAttempts;
    private final long deleteBackoffMs;

    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    private volatile String collectionId;

    /** 按集合名缓存 Chroma UUID；用于多集合版本化管理 */
    private final ConcurrentHashMap<String, String> collectionIdByCollection = new ConcurrentHashMap<>();

    private static final String CHROMA_HINT_ZH =
            "向量库 Chroma 操作失败。常见原因：Chroma 中 collection 被删除/重建后，本进程仍缓存旧的 collection UUID。"
                    + " 本次已对 404 自动清空缓存并重绑一次；若仍失败请核对 chroma.url、chroma.collection 与 Chroma 服务，必要时重启 novel-splitter 后重新向量化。";

    public ChromaVectorStore(
            RestClient.Builder builder,
            @Value("${chroma.url:http://localhost:8081}") String chromaUrl,
            @Value("${chroma.collection:novel-splitter}") String collectionName,
            @Value("${chroma.hnsw-space:cosine}") String hnswSpace,
            @Value("${chroma.init.fail-on-distance-mismatch:true}") boolean failOnDistanceMismatch,
            @Value("${chroma.init.eager:true}") boolean eagerInit,
            @Value("${chroma.delete.max-attempts:4}") int deleteMaxAttempts,
            @Value("${chroma.delete.backoff-ms:200}") long deleteBackoffMs) {
        this.collectionName = collectionName;
        this.hnswSpace = normalizeConfiguredSpace(hnswSpace);
        this.failOnDistanceMismatch = failOnDistanceMismatch;
        this.eagerInit = eagerInit;
        this.deleteMaxAttempts = Math.max(1, deleteMaxAttempts);
        this.deleteBackoffMs = Math.max(0L, deleteBackoffMs);
        this.restClient = builder.baseUrl(chromaUrl).build();
    }

    private static String normalizeConfiguredSpace(String raw) {
        if (raw == null || raw.isBlank()) {
            return "cosine";
        }
        return raw.trim().toLowerCase();
    }

    /**
     * 启动时幂等绑定 collection（get 或 create），校验距离度量，并打印向量总数便于确认数据是否落库。
     */
    @PostConstruct
    public void initChromaCollection() {
        if (!eagerInit) {
            log.info("已禁用 Chroma 启动时预绑定（chroma.init.eager=false）；collection 将在首次使用时绑定。");
            return;
        }
        synchronized (this) {
            bindCollectionLocked("startup");
        }
        long n = count();
        log.info("Chroma collection '{}' 已就绪（{}={}），向量总数={}",
                collectionName, CHROMA_HNSW_SPACE_KEY, hnswSpace, n);
    }

    @Override
    public void save(Scene scene, float[] embedding) {
        saveBatch(Collections.singletonList(scene), Collections.singletonList(embedding));
    }

    @Override
    public void saveBatch(List<Scene> scenes, List<float[]> embeddings) {
        ensureCollectionExists();

        if (scenes.isEmpty()) {
            return;
        }

        List<String> ids = scenes.stream().map(Scene::getId).collect(Collectors.toList());
        List<Map<String, Object>> metadatas = scenes.stream()
                .map(this::buildChromaMetadata)
                .collect(Collectors.toList());

        List<String> documents = scenes.stream().map(Scene::getText).collect(Collectors.toList());

        List<List<Double>> embeddingsList = embeddings.stream()
                .map(this::toDoubleList)
                .collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("ids", ids);
        request.put("embeddings", embeddingsList);
        request.put("metadatas", metadatas);
        request.put("documents", documents);

        log.debug("写入 {} 个场景到 ChromaDB", scenes.size());

        try {
            postAddPayload(request);
        } catch (RestClientResponseException e) {
            if (!isStaleCollectionNotFound(e)) {
                throw chromaUserVisibleFailure("写入向量(/add)", e);
            }
            log.warn("Chroma /add 返回 404（collection id 已失效）；已清空缓存并重新绑定 collection '{}' 一次。", collectionName);
            invalidateCachedCollectionId();
            ensureCollectionExists();
            try {
                postAddPayload(request);
            } catch (RestClientResponseException e2) {
                throw chromaUserVisibleFailure("写入向量(/add)，自动重绑后仍失败", e2);
            }
        }

        log.info("已写入 {} 条向量到 ChromaDB 集合 '{}'", scenes.size(), collectionName);
    }

    /**
     * 与写入、{@code where} 删除、检索过滤对齐的必填 metadata；缺失则拒绝写入以免产生「查不到也不报错」的脏数据。
     * <p>包可见（非 private）：供测试直接构造校验结构化键的写入逻辑。</p>
     */
    Map<String, Object> buildChromaMetadata(Scene s) {
        Map<String, Object> map = new HashMap<>();
        if (s.getMetadata() != null) {
            if (s.getMetadata().getNovel() != null) {
                map.put("novelId", s.getMetadata().getNovel());
            }
            if (s.getMetadata().getVersion() != null) {
                map.put("version", s.getMetadata().getVersion());
            }
            if (s.getMetadata().getChunkSize() != null) {
                map.put("chunkSize", s.getMetadata().getChunkSize());
            }
            if (s.getMetadata().getChunkOverlap() != null) {
                map.put("chunkOverlap", s.getMetadata().getChunkOverlap());
            }
            if (s.getMetadata().getSequenceNum() != null) {
                map.put("sequenceNum", s.getMetadata().getSequenceNum());
            }
            // 可选语义分析键：字段为 null（或 characters 为空列表）则不写入，不参与必填校验
            if (s.getMetadata().getRole() != null) {
                map.put("role", s.getMetadata().getRole());
            }
            if (s.getMetadata().getLocation() != null) {
                map.put("location", s.getMetadata().getLocation());
            }
            if (s.getMetadata().getTime() != null) {
                map.put("time", s.getMetadata().getTime());
            }
            if (s.getMetadata().getCharacters() != null && !s.getMetadata().getCharacters().isEmpty()) {
                map.put("characters", s.getMetadata().getCharacters());
            }
        }
        map.put("chapterIndex", s.getChapterIndex());
        if (s.getChapterTitle() != null) {
            map.put("chapterTitle", s.getChapterTitle());
        }
        if (s.getPersistenceId() != null) {
            map.put("sceneId", s.getPersistenceId());
        }
        if (s.getMetadata() != null && s.getMetadata().getSequenceNum() != null) {
            map.put("chunkIndex", s.getMetadata().getSequenceNum());
        }
        validateChromaSceneMetadata(s, map);
        return map;
    }

    private void validateChromaSceneMetadata(Scene s, Map<String, Object> map) {
        List<String> missing = new ArrayList<>();
        if (!map.containsKey("novelId")) {
            missing.add("novelId");
        }
        if (!map.containsKey("version")) {
            missing.add("version");
        }
        if (!map.containsKey("chunkSize")) {
            missing.add("chunkSize");
        }
        if (!map.containsKey("chunkOverlap")) {
            missing.add("chunkOverlap");
        }
        if (!map.containsKey("sequenceNum")) {
            missing.add("sequenceNum");
        }
        if (!map.containsKey("sceneId")) {
            missing.add("sceneId");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Chroma metadata missing required keys " + missing
                            + " (need DB persistenceId + SceneMetadata for embed). sceneChunkKey=" + s.getId());
        }
        if (!(map.get("sceneId") instanceof Number)) {
            throw new IllegalArgumentException("Chroma metadata sceneId must be numeric (DB persistence id). sceneChunkKey=" + s.getId());
        }
    }

    private void postAddPayload(Map<String, Object> request) {
        restClient.post()
                .uri(collectionUri("/add"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void delete(Map<String, Object> filter) {
        ensureCollectionExists();

        if (filter == null || filter.isEmpty()) {
            log.warn("收到空 filter 的删除请求，已忽略以避免误删数据。如需清空全部请使用 reset()。");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("where", buildWhereClause(filter));

        int max = deleteMaxAttempts;
        for (int attempt = 1; attempt <= max; attempt++) {
            try {
                postDeleteWithOptionalStaleRebind(request);
                log.info("已从 ChromaDB collection '{}' 删除文档，过滤条件：{}", collectionName, filter);
                return;
            } catch (RestClientResponseException e) {
                if (attempt < max && isTransientChromaDeleteResponse(e)) {
                    log.warn(
                            "Chroma /delete 遇到临时性 HTTP {}（第 {}/{} 次尝试）；将在 {}ms 后重试",
                            e.getStatusCode().value(),
                            attempt,
                            max,
                            deleteBackoffMs * attempt);
                    sleepDeleteBackoff(attempt);
                    continue;
                }
                throw chromaUserVisibleFailure("删除向量(/delete)", e);
            } catch (RestClientException e) {
                if (attempt < max) {
                    log.warn("Chroma /delete 出现临时性客户端错误（第 {}/{} 次尝试）：{}；将在 {}ms 后重试",
                            attempt, max, e.getMessage(), deleteBackoffMs * attempt);
                    sleepDeleteBackoff(attempt);
                    continue;
                }
                throw new IllegalStateException(CHROMA_HINT_ZH + " [删除向量(/delete)] " + e.getMessage(), e);
            }
        }
    }

    /**
     * 单次删除；遇「缓存的 collection id 已失效」时重绑后再删一次。
     */
    private void postDeleteWithOptionalStaleRebind(Map<String, Object> request) {
        try {
            postDeletePayload(request);
        } catch (RestClientResponseException e) {
            if (!isStaleCollectionNotFound(e)) {
                throw e;
            }
            log.warn("Chroma /delete 返回 404（collection id 已失效）；已清空缓存并重新绑定 collection '{}' 一次。", collectionName);
            invalidateCachedCollectionId();
            ensureCollectionExists();
            postDeletePayload(request);
        }
    }

    private void sleepDeleteBackoff(int attemptIndexOneBased) {
        long ms = deleteBackoffMs * attemptIndexOneBased;
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during Chroma delete backoff", ie);
        }
    }

    private static boolean isTransientChromaDeleteResponse(RestClientResponseException e) {
        int code = e.getStatusCode().value();
        if (code == HttpStatus.REQUEST_TIMEOUT.value() || code == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return true;
        }
        return e.getStatusCode().is5xxServerError();
    }

    private void postDeletePayload(Map<String, Object> request) {
        restClient.post()
                .uri(collectionUri("/delete"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void reset() {
        synchronized (this) {
            this.collectionId = null;
            String path = collectionsBasePath() + "/" + encodedCollectionNameSegment();
            try {
                restClient.delete()
                        .uri(path)
                        .retrieve()
                        .toBodilessEntity();
                log.info("已删除 ChromaDB collection: {}", collectionName);
            } catch (RestClientResponseException e) {
                if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                    log.info("重置时未找到 Chroma collection '{}'（正常）", collectionName);
                } else {
                    log.warn("删除 collection {} 失败：{}", collectionName, e.getMessage());
                }
            } catch (Exception e) {
                log.warn("删除 collection {} 失败：{}", collectionName, e.getMessage());
            }
            bindCollectionLocked("after-reset");
        }
        log.info("已重置 ChromaDB collection: {}", collectionName);
    }

    @Override
    public long count() {
        ensureCollectionExists();
        try {
            return getCountBody();
        } catch (RestClientResponseException e) {
            if (!isStaleCollectionNotFound(e)) {
                log.error("从 ChromaDB 获取向量总数失败", e);
                return -1;
            }
            log.warn("Chroma /count 返回 404（collection id 已失效）；已清空缓存并重新绑定 '{}' 一次。", collectionName);
            invalidateCachedCollectionId();
            ensureCollectionExists();
            try {
                return getCountBody();
            } catch (RestClientResponseException e2) {
                log.error("重绑后从 ChromaDB 获取向量总数仍失败", e2);
                return -1;
            }
        } catch (Exception e) {
            log.error("从 ChromaDB 获取向量总数失败", e);
            return -1;
        }
    }

    private Long getCountBody() {
        return restClient.get()
                .uri(collectionUri("/count"))
                .retrieve()
                .body(Long.class);
    }

    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        ensureCollectionExists();

        List<Double> embeddingList = toDoubleList(queryEmbedding);

        Map<String, Object> request = new HashMap<>();
        request.put("query_embeddings", Collections.singletonList(embeddingList));
        request.put("n_results", topK);
        request.put("include", Arrays.asList("distances", "metadatas"));

        if (filter != null && !filter.isEmpty()) {
            request.put("where", buildWhereClause(filter));
        }

        ChromaQueryResponse response;
        try {
            response = postQueryPayload(request);
        } catch (RestClientResponseException e) {
            if (!isStaleCollectionNotFound(e)) {
                throw chromaUserVisibleFailure("检索(/query)", e);
            }
            log.warn("Chroma /query 返回 404（collection id 已失效）；已清空缓存并重新绑定 '{}' 一次。", collectionName);
            invalidateCachedCollectionId();
            ensureCollectionExists();
            try {
                response = postQueryPayload(request);
            } catch (RestClientResponseException e2) {
                throw chromaUserVisibleFailure("检索(/query)，自动重绑后仍失败", e2);
            }
        }

        if (response == null || response.getIds() == null || response.getIds().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resultIds = response.getIds().get(0);
        List<Double> distances = response.getDistances().get(0);
        List<Map<String, Object>> resultMetas =
                (response.getMetadatas() != null && !response.getMetadatas().isEmpty()) ? response.getMetadatas().get(0) : null;

        return IntStream.range(0, resultIds.size())
                .mapToObj(i -> {
                    Map<String, Object> meta = null;
                    if (resultMetas != null && i < resultMetas.size()) {
                        meta = resultMetas.get(i);
                    }
                    return new VectorRecord(
                            resultIds.get(i),
                            1.0 - distances.get(i),
                            meta
                    );
                })
                .collect(Collectors.toList());
    }

    private ChromaQueryResponse postQueryPayload(Map<String, Object> request) {
        return restClient.post()
                .uri(collectionUri("/query"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChromaQueryResponse.class);
    }

    private void invalidateCachedCollectionId() {
        synchronized (this) {
            this.collectionId = null;
        }
        log.warn("已清空逻辑名称 '{}' 的 Chroma collection UUID 缓存", collectionName);
    }

    private static boolean isStaleCollectionNotFound(RestClientResponseException e) {
        if (!HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
            return false;
        }
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return true;
        }
        return body.contains("does not exist")
                || body.contains("NotFoundError")
                || body.contains("not found");
    }

    private static IllegalStateException chromaUserVisibleFailure(String op, RestClientResponseException e) {
        return new IllegalStateException(CHROMA_HINT_ZH + " [" + op + "] HTTP " + e.getStatusCode().value()
                + " " + e.getResponseBodyAsString(), e);
    }

    private void ensureCollectionExists() {
        if (collectionId != null) {
            return;
        }
        synchronized (this) {
            if (collectionId != null) {
                return;
            }
            bindCollectionLocked("lazy");
        }
    }

    /**
     * 幂等：按名称 GET；不存在则带 {@code hnsw:space} POST 创建；与配置不一致时可按 {@code failOnDistanceMismatch} 终止启动。
     */
    private void bindCollectionLocked(String reason) {
        ChromaCollection existing = getCollectionByNameOrNull();
        if (existing != null && existing.getId() != null) {
            assertCollectionDistance(existing);
            this.collectionId = existing.getId();
            log.info("已绑定 ChromaDB collection '{}' -> id {}（{}）", collectionName, collectionId, reason);
            return;
        }

        Map<String, Object> createBody = Map.of(
                "name", collectionName,
                "metadata", Map.of(CHROMA_HNSW_SPACE_KEY, hnswSpace)
        );
        try {
            ChromaCollection created = restClient.post()
                    .uri(collectionsBasePath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createBody)
                    .retrieve()
                    .body(ChromaCollection.class);
            if (created != null && created.getId() != null) {
                this.collectionId = created.getId();
                log.info("已创建 ChromaDB collection: {}（{}）（{}）", collectionName, collectionId, reason);
                return;
            }
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                ChromaCollection race = getCollectionByNameOrNull();
                if (race != null && race.getId() != null) {
                    assertCollectionDistance(race);
                    this.collectionId = race.getId();
                    log.info("创建并发竞争后已绑定已有 ChromaDB collection: {}（{}）（{}）", collectionName, collectionId, reason);
                    return;
                }
            }
            throw new IllegalStateException("Failed to create ChromaDB collection '" + collectionName + "': HTTP "
                    + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create ChromaDB collection: " + collectionName, e);
        }
        throw new IllegalStateException("Failed to initialize ChromaDB collection " + collectionName);
    }

    private ChromaCollection getCollectionByNameOrNull() {
        try {
            return restClient.get()
                    .uri(collectionsBasePath() + "/" + encodedCollectionNameSegment())
                    .retrieve()
                    .body(ChromaCollection.class);
        } catch (RestClientResponseException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                return null;
            }
            throw new IllegalStateException("Failed to GET Chroma collection '" + collectionName + "': HTTP "
                    + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
        }
    }

    private void assertCollectionDistance(ChromaCollection coll) {
        String actual = extractHnswSpace(coll.getMetadata());
        if (actual == null || actual.isBlank()) {
            String msg = "Chroma collection '" + collectionName
                    + "' 已存在，但其 metadata 中缺少 " + CHROMA_HNSW_SPACE_KEY + "（无法校验距离度量）。"
                    + "期望 " + CHROMA_HNSW_SPACE_KEY + "=" + hnswSpace + "。请删除并重建 collection，或调整 chroma.hnsw-space 配置。";
            if (failOnDistanceMismatch) {
                throw new IllegalStateException(msg);
            }
            log.warn("{}（fail-on-distance-mismatch=false）", msg);
            return;
        }
        if (!hnswSpace.equalsIgnoreCase(actual)) {
            String msg = "Chroma collection '" + collectionName + "' 的 " + CHROMA_HNSW_SPACE_KEY + "=" + actual
                    + "，但应用期望的是 " + hnswSpace + "（chroma.hnsw-space）。距离度量无法原地修改；"
                    + "请删除该 collection 或使用管理端重建。";
            if (failOnDistanceMismatch) {
                throw new IllegalStateException(msg);
            }
            log.error("{}（fail-on-distance-mismatch=false；检索分数可能不准）", msg);
        }
    }

    private static String extractHnswSpace(Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object v = metadata.get(CHROMA_HNSW_SPACE_KEY);
        if (v == null) {
            return null;
        }
        return String.valueOf(v).trim().toLowerCase();
    }

    private List<Double> toDoubleList(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return Collections.emptyList();
        }
        return IntStream.range(0, embedding.length)
                .mapToObj(i -> (double) embedding[i])
                .collect(Collectors.toList());
    }

    private String collectionUri(String suffix) {
        String base = collectionsBasePath() + "/" + collectionId;
        if (suffix == null || suffix.isBlank()) {
            return base;
        }
        return base + suffix;
    }

    private String collectionsBasePath() {
        return "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections";
    }

    private String encodedCollectionNameSegment() {
        return UriUtils.encodePathSegment(collectionName, StandardCharsets.UTF_8);
    }

    private Map<String, Object> buildWhereClause(Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Map<String, Object>> clauses = filter.entrySet().stream()
                .map(entry -> Map.<String, Object>of(entry.getKey(), buildOperatorClause(entry.getValue())))
                .collect(Collectors.toList());

        if (clauses.size() == 1) {
            return clauses.get(0);
        }
        return Map.of("$and", clauses);
    }

    private Map<String, Object> buildOperatorClause(Object value) {
        if (value == null) {
            return Map.of("$eq", null);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return converted;
        }
        if (value instanceof List<?> list) {
            return Map.of("$in", list);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(Array.get(value, i));
            }
            return Map.of("$in", list);
        }
        return Map.of("$eq", value);
    }

    // ──────────── 多集合：按名称解析 Chroma UUID ────────────

    /**
     * 按集合名称懒绑定 / 创建，返回 Chroma UUID。
     */
    private String resolveOrCreateCollectionId(String colName) {
        String cached = collectionIdByCollection.get(colName);
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            cached = collectionIdByCollection.get(colName);
            if (cached != null) {
                return cached;
            }
            // 特殊路径：如果 colName 就是默认 collectionName，直接用已有绑定
            if (colName.equals(collectionName) && collectionId != null) {
                collectionIdByCollection.put(colName, collectionId);
                return collectionId;
            }
            // 按名称 GET
            ChromaCollection existing = getCollectionByNameOrNull(colName);
            if (existing != null && existing.getId() != null) {
                assertCollectionDistance(existing);
                collectionIdByCollection.put(colName, existing.getId());
                if (colName.equals(collectionName)) {
                    this.collectionId = existing.getId();
                }
                log.info("已绑定 ChromaDB collection '{}' -> id {}", colName, existing.getId());
                return existing.getId();
            }
            // POST 创建
            Map<String, Object> createBody = Map.of(
                    "name", colName,
                    "metadata", Map.of(CHROMA_HNSW_SPACE_KEY, hnswSpace)
            );
            try {
                ChromaCollection created = restClient.post()
                        .uri(collectionsBasePath())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(createBody)
                        .retrieve()
                        .body(ChromaCollection.class);
                if (created != null && created.getId() != null) {
                    collectionIdByCollection.put(colName, created.getId());
                    if (colName.equals(collectionName)) {
                        this.collectionId = created.getId();
                    }
                    log.info("已创建 ChromaDB collection: {} -> id {}", colName, created.getId());
                    return created.getId();
                }
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().is4xxClientError()) {
                    ChromaCollection race = getCollectionByNameOrNull(colName);
                    if (race != null && race.getId() != null) {
                        assertCollectionDistance(race);
                        collectionIdByCollection.put(colName, race.getId());
                        if (colName.equals(collectionName)) {
                            this.collectionId = race.getId();
                        }
                        log.info("创建并发竞争后已绑定已有 ChromaDB collection: {} -> {}", colName, race.getId());
                        return race.getId();
                    }
                }
                throw new IllegalStateException("Failed to create ChromaDB collection '" + colName + "': HTTP "
                        + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create ChromaDB collection: " + colName, e);
            }
            throw new IllegalStateException("Failed to initialize ChromaDB collection " + colName);
        }
    }

    private ChromaCollection getCollectionByNameOrNull(String colName) {
        try {
            return restClient.get()
                    .uri(collectionsBasePath() + "/" + UriUtils.encodePathSegment(colName, StandardCharsets.UTF_8))
                    .retrieve()
                    .body(ChromaCollection.class);
        } catch (RestClientResponseException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                return null;
            }
            throw new IllegalStateException("Failed to GET Chroma collection '" + colName + "': HTTP "
                    + e.getStatusCode().value() + " " + e.getResponseBodyAsString(), e);
        }
    }

    private String collectionUri(String colId, String suffix) {
        String base = collectionsBasePath() + "/" + colId;
        if (suffix == null || suffix.isBlank()) {
            return base;
        }
        return base + suffix;
    }

    @Override
    public void deleteByCollection(String colName) {
        String colId = collectionIdByCollection.get(colName);
        if (colId == null) {
            ChromaCollection existing = getCollectionByNameOrNull(colName);
            if (existing == null || existing.getId() == null) {
                log.info("Collection '{}' 不存在，deleteByCollection 按成功处理", colName);
                return;
            }
            colId = existing.getId();
            collectionIdByCollection.put(colName, colId);
        }
        try {
            restClient.delete()
                    .uri(collectionsBasePath() + "/" + UriUtils.encodePathSegment(colName, StandardCharsets.UTF_8))
                    .retrieve()
                    .toBodilessEntity();
            log.info("已删除 ChromaDB collection: {}", colName);
        } catch (RestClientResponseException e) {
            if (HttpStatus.NOT_FOUND.equals(e.getStatusCode())) {
                log.info("Chroma collection '{}' 已被删除（正常）", colName);
            } else {
                throw new IllegalStateException("Failed to delete Chroma collection '" + colName + "': HTTP "
                        + e.getStatusCode().value(), e);
            }
        }
        collectionIdByCollection.remove(colName);
        if (colName.equals(collectionName)) {
            synchronized (this) {
                this.collectionId = null;
            }
        }
    }

    // ──────────── 集合感知的写 / 查 ────────────

    @Override
    public void saveBatch(List<Scene> scenes, List<float[]> embeddings, String colName) {
        if (scenes.isEmpty()) {
            return;
        }

        String colId = resolveOrCreateCollectionId(colName);

        List<String> ids = scenes.stream().map(Scene::getId).collect(Collectors.toList());
        List<Map<String, Object>> metadatas = scenes.stream()
                .map(this::buildChromaMetadata)
                .collect(Collectors.toList());
        List<String> documents = scenes.stream().map(Scene::getText).collect(Collectors.toList());
        List<List<Double>> embeddingsList = embeddings.stream()
                .map(this::toDoubleList)
                .collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("ids", ids);
        request.put("embeddings", embeddingsList);
        request.put("metadatas", metadatas);
        request.put("documents", documents);

        log.debug("写入 {} 个场景到 ChromaDB 集合 '{}'", scenes.size(), colName);

        try {
            restClient.post()
                    .uri(collectionUri(colId, "/add"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw chromaUserVisibleFailure("写入向量(/add) collection=" + colName, e);
        }

        log.info("已写入 {} 条向量到 ChromaDB 集合 '{}'", scenes.size(), colName);
    }

    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter,
                                     String colName) {
        String colId = resolveOrCreateCollectionId(colName);

        List<Double> embeddingList = toDoubleList(queryEmbedding);

        Map<String, Object> request = new HashMap<>();
        request.put("query_embeddings", Collections.singletonList(embeddingList));
        request.put("n_results", topK);
        request.put("include", Arrays.asList("distances", "metadatas"));

        if (filter != null && !filter.isEmpty()) {
            request.put("where", buildWhereClause(filter));
        }

        ChromaQueryResponse response;
        try {
            response = restClient.post()
                    .uri(collectionUri(colId, "/query"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChromaQueryResponse.class);
        } catch (RestClientResponseException e) {
            throw chromaUserVisibleFailure("检索(/query) collection=" + colName, e);
        }

        if (response == null || response.getIds() == null || response.getIds().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> resultIds = response.getIds().get(0);
        List<Double> distances = response.getDistances().get(0);
        List<Map<String, Object>> resultMetas =
                (response.getMetadatas() != null && !response.getMetadatas().isEmpty()) ? response.getMetadatas().get(0) : null;

        return IntStream.range(0, resultIds.size())
                .mapToObj(i -> {
                    Map<String, Object> meta = null;
                    if (resultMetas != null && i < resultMetas.size()) {
                        meta = resultMetas.get(i);
                    }
                    return new VectorRecord(
                            resultIds.get(i),
                            1.0 - distances.get(i),
                            meta
                    );
                })
                .collect(Collectors.toList());
    }

    // ──────────── 集合生命周期 ────────────

    @Override
    public void createCollection(String name) {
        try {
            resolveOrCreateCollectionId(name);
            log.info("Collection '{}' 已就绪（已创建或已存在）", name);
        } catch (Exception e) {
            log.warn("创建 collection '{}' 失败：{}", name, e.getMessage());
            throw new IllegalStateException("Failed to create collection: " + name, e);
        }
    }

    @Override
    public void deleteCollection(String name) {
        deleteByCollection(name);
    }

    @Override
    public boolean collectionExists(String name) {
        String cached = collectionIdByCollection.get(name);
        if (cached != null) {
            return true;
        }
        try {
            ChromaCollection col = getCollectionByNameOrNull(name);
            if (col != null && col.getId() != null) {
                collectionIdByCollection.put(name, col.getId());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("检查 collection '{}' 是否存在失败：{}", name, e.getMessage());
            return false;
        }
    }
}
