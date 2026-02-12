package com.novel.splitter.application.controller;

import com.novel.splitter.domain.model.embedding.VectorRecord;
import com.novel.splitter.embedding.api.EmbeddingService;
import com.novel.splitter.embedding.api.VectorStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/vector")
@RequiredArgsConstructor
@Slf4j
public class VectorManagementController {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            return ResponseEntity.ok(Map.of(
                "count", vectorStore.count(),
                "type", vectorStore.getClass().getSimpleName()
            ));
        } catch (Exception e) {
            log.error("Failed to get vector stats", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody VectorSearchRequest request) {
        try {
            if (request.getQuery() == null || request.getQuery().isBlank()) {
                return ResponseEntity.badRequest().body("Query is required");
            }
            float[] embedding = embeddingService.embed(request.getQuery());
            return ResponseEntity.ok(vectorStore.search(embedding, request.getTopK(), request.getFilter()));
        } catch (Exception e) {
            log.error("Vector search failed", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestBody Map<String, Object> filter) {
        vectorStore.delete(filter);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        vectorStore.reset();
        return ResponseEntity.ok().build();
    }

    @Data
    public static class VectorSearchRequest {
        private String query;
        private int topK = 5;
        private Map<String, Object> filter;
    }
}
