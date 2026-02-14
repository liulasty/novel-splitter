package com.novel.splitter.embedding.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.domain.model.embedding.chroma.ChromaCollection;
import com.novel.splitter.domain.model.embedding.chroma.ChromaQueryResponse;
import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.Builder;
import lombok.Data;
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

@Slf4j
@Component
@ConditionalOnProperty(name = "embedding.store.type", havingValue = "chroma")
@RequiredArgsConstructor
public class ChromaVectorStore implements VectorStore {

    @Value("${chroma.url:http://localhost:8081}")
    private String chromaUrl;

    @Value("${chroma.collection:novel-splitter}")
    private String collectionName;

    private static final String DEFAULT_TENANT = "default_tenant";
    private static final String DEFAULT_DATABASE = "default_database";

    private final RestClient restClient = RestClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String collectionId;

    @Override
    public void save(Scene scene, float[] embedding) {
        saveBatch(Collections.singletonList(scene), Collections.singletonList(embedding));
    }

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
                            }
                            return map;
                        })
                        .collect(Collectors.toList());
                
        List<String> documents = scenes.stream().map(Scene::getText).collect(Collectors.toList());

        // Convert float[] to List<Double> for Chroma API
        List<List<Double>> embeddingsList = embeddings.stream()
                .map(arr -> IntStream.range(0, arr.length)
                        .mapToDouble(i -> arr[i])
                        .boxed()
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("ids", ids);
        request.put("embeddings", embeddingsList);
        request.put("metadatas", metadatas);
        request.put("documents", documents);

        try {
            String json = objectMapper.writeValueAsString(request);
            // log.info("Sending request to ChromaDB: {}", json);
        } catch (Exception e) {
            e.printStackTrace();
        }

        restClient.post()
                .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        
        log.info("Saved {} vectors to ChromaDB collection '{}'", scenes.size(), collectionName);
    }

    @Override
    public void delete(Map<String, Object> filter) {
        ensureCollectionExists();
        
        if (filter == null || filter.isEmpty()) {
            log.warn("Delete called with empty filter, ignoring to avoid accidental data loss. Use reset() to clear all.");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        
        if (filter.size() == 1) {
            request.put("where", filter);
        } else {
            List<Map<String, Object>> andList = new ArrayList<>();
            filter.forEach((k, v) -> andList.add(Collections.singletonMap(k, v)));
            request.put("where", Collections.singletonMap("$and", andList));
        }

        restClient.post()
                .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionId + "/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        
        log.info("Deleted documents from ChromaDB collection '{}' with filter: {}", collectionName, filter);
    }

    @Override
    public void reset() {
        if (collectionId == null) {
            ensureCollectionExists();
        }
        
        // Delete the collection
        try {
            restClient.delete()
                    .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionName)
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

    @Override
    public long count() {
        ensureCollectionExists();
        try {
            return restClient.get()
                    .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionId + "/count")
                    .retrieve()
                    .body(Long.class);
        } catch (Exception e) {
            log.error("Failed to get count from ChromaDB", e);
            return -1;
        }
    }

    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK, Map<String, Object> filter) {
        ensureCollectionExists();

        List<Double> embeddingList = IntStream.range(0, queryEmbedding.length)
                .mapToDouble(i -> queryEmbedding[i])
                .boxed()
                .collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("query_embeddings", Collections.singletonList(embeddingList));
        request.put("n_results", topK);
        // We need ids, distances, and metadatas
        request.put("include", Arrays.asList("distances", "metadatas")); 
        
        if (filter != null && !filter.isEmpty()) {
            if (filter.size() == 1) {
                request.put("where", filter);
            } else {
                List<Map<String, Object>> andList = new ArrayList<>();
                filter.forEach((k, v) -> andList.add(Collections.singletonMap(k, v)));
                request.put("where", Collections.singletonMap("$and", andList));
            }
        }

        ChromaQueryResponse response = restClient.post()
                .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionId + "/query")
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
                            1.0 - distances.get(i), // Convert distance to similarity score approx
                            meta
                    );
                })
                .collect(Collectors.toList());
    }

    private void ensureCollectionExists() {
        if (collectionId != null) return;

        try {
            // List collections to check if it exists
            List<ChromaCollection> collections = restClient.get()
                    .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections")
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<ChromaCollection>>() {});
            
            if (collections != null) {
                for (ChromaCollection collection : collections) {
                    if (collectionName.equals(collection.getName())) {
                        this.collectionId = collection.getId();
                        log.info("Found existing ChromaDB collection: {} ({})", collectionName, collectionId);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to list collections, trying to create new one. Error: {}", e.getMessage());
        }

        // Create collection
        Map<String, String> request = Collections.singletonMap("name", collectionName);
        ChromaCollection newCollection = restClient.post()
                .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChromaCollection.class);

        if (newCollection != null) {
            this.collectionId = newCollection.getId();
            log.info("Created ChromaDB collection: {} ({})", collectionName, collectionId);
        } else {
            throw new RuntimeException("Failed to create ChromaDB collection");
        }
    }
}
