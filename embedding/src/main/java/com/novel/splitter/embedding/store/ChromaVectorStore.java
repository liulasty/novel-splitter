package com.novel.splitter.embedding.store;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.splitter.domain.model.Scene;
import com.novel.splitter.embedding.api.VectorRecord;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                            map.put("chapter_title", s.getChapterTitle());
                            map.put("start_paragraph_index", s.getStartParagraphIndex());
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

        restClient.post()
                .uri(chromaUrl + "/api/v2/tenants/" + DEFAULT_TENANT + "/databases/" + DEFAULT_DATABASE + "/collections/" + collectionId + "/add")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
        
        log.info("Saved {} vectors to ChromaDB collection '{}'", scenes.size(), collectionName);
    }

    @Override
    public List<VectorRecord> search(float[] queryEmbedding, int topK) {
        ensureCollectionExists();

        List<Double> embeddingList = IntStream.range(0, queryEmbedding.length)
                .mapToDouble(i -> queryEmbedding[i])
                .boxed()
                .collect(Collectors.toList());

        Map<String, Object> request = new HashMap<>();
        request.put("query_embeddings", Collections.singletonList(embeddingList));
        request.put("n_results", topK);
        // We only need ids and distances
        request.put("include", Collections.singletonList("distances")); 

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

        return IntStream.range(0, resultIds.size())
                .mapToObj(i -> new VectorRecord(
                        resultIds.get(i),
                        1.0 - distances.get(i) // Convert distance to similarity score approx
                ))
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

    @Data
    private static class ChromaCollection {
        private String id;
        private String name;
    }

    @Data
    private static class ChromaQueryResponse {
        private List<List<String>> ids;
        private List<List<Double>> distances;
        private List<List<Object>> metadatas;
    }
}
