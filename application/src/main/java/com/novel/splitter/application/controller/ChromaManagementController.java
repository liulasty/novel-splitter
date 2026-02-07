package com.novel.splitter.application.controller;

import com.novel.splitter.embedding.api.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/chroma")
@RequiredArgsConstructor
@Slf4j
public class ChromaManagementController {

    private final VectorStore vectorStore;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            long count = vectorStore.count();
            return ResponseEntity.ok(Map.of(
                "count", count,
                "storeType", vectorStore.getClass().getSimpleName()
            ));
        } catch (Exception e) {
            log.error("Failed to get Chroma stats", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> reset() {
        try {
            vectorStore.reset();
            return ResponseEntity.ok(Map.of("message", "Database reset successfully"));
        } catch (Exception e) {
            log.error("Failed to reset Chroma", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, String>> delete(@RequestBody Map<String, Object> filter) {
        try {
            if (filter == null || filter.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Filter cannot be empty"));
            }
            vectorStore.delete(filter);
            return ResponseEntity.ok(Map.of("message", "Documents deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete documents", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
